package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.config.mobile.JwtTokenProvider;
import com.smsweb.sms.dto.mobile.*;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.FamilyAccount;
import com.smsweb.sms.services.mobile.FamilyAccountService;
import com.smsweb.sms.services.mobile.LoginAttemptService;
import com.smsweb.sms.services.mobile.MobileRefreshTokenService;
import com.smsweb.sms.services.mobile.ParentSessionStore;
import com.smsweb.sms.services.student.AcademicStudentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Mobile Authentication Controller — mobile number + FamilyAccount password.
 *
 * POST /api/v1/auth/login
 *   Authenticates parent via FamilyAccount (mobile + password).
 *   Single child  → full JWT.
 *   Multi child   → temp token + child list → call /select-child.
 *
 * POST /api/v1/auth/select-child
 *   Exchanges temp token + chosen academicStudentId for a full JWT.
 *
 * POST /api/v1/auth/change-password
 *   Authenticated parent changes their FamilyAccount password.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class MobileAuthController {

    private static final Logger log = LoggerFactory.getLogger(MobileAuthController.class);

    private final FamilyAccountService     familyAccountService;
    private final AcademicStudentService   academicStudentService;
    private final JwtTokenProvider         jwtTokenProvider;
    private final ParentSessionStore       parentSessionStore;
    private final MobileRefreshTokenService refreshTokenService;   // new, mobile-only (feature #10)
    private final LoginAttemptService      loginAttemptService;    // new, mobile-only — login throttling

    public MobileAuthController(FamilyAccountService familyAccountService,
                                AcademicStudentService academicStudentService,
                                JwtTokenProvider jwtTokenProvider,
                                ParentSessionStore parentSessionStore,
                                MobileRefreshTokenService refreshTokenService,
                                LoginAttemptService loginAttemptService) {
        this.familyAccountService   = familyAccountService;
        this.academicStudentService = academicStudentService;
        this.jwtTokenProvider       = jwtTokenProvider;
        this.parentSessionStore     = parentSessionStore;
        this.refreshTokenService    = refreshTokenService;
        this.loginAttemptService    = loginAttemptService;
    }

    // ── POST /api/v1/auth/login ───────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(
            @Valid @RequestBody MobileLoginRequest request) {
        log.info("Inside login");

        // 0. Throttle — previously unlimited password guesses were possible
        // against any mobile number with no slowdown at all.
        String mobile = request.getMobile();
        if (loginAttemptService.isLocked(mobile)) {
            long minutes = loginAttemptService.remainingLockoutMinutes(mobile);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Too many failed attempts. Try again in " + minutes + " minute(s)."));
        }

        // 1. Find FamilyAccount by mobile
        FamilyAccount account = familyAccountService.findActive(mobile)
                .orElse(null);

        if (account == null) {
            loginAttemptService.recordFailure(mobile);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("No account found for this mobile number. Contact school admin."));
        }

        // 2. Verify password against FamilyAccount (BCrypt)
        if (!familyAccountService.verifyPassword(account, request.getPassword())) {
            loginAttemptService.recordFailure(mobile);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Incorrect password"));
        }
        loginAttemptService.recordSuccess(mobile);

        // 3. Find children — SiblingGroup first, fallback to FamilyAccount FK
        //    SiblingGroup: admin-defined family grouping (handles different mobiles per child)
        //    Fallback:     FamilyAccount FK via mobile1 (automatic, no admin setup needed)
        List<AcademicStudent> students =
                academicStudentService.findSiblingsByMobile(request.getMobile());

        if (students.isEmpty()) {
            // No sibling group set up — use FamilyAccount FK fallback
            students = academicStudentService.findActiveByFamilyAccount(account);
        }

        if (students.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No active students linked to this mobile number"));
        }

        // 4a. Single child → full JWT
        if (students.size() == 1) {
            AcademicStudent as = students.get(0);
            String token = buildJwt(as);
            String refreshToken = refreshTokenService.issueToken(as);
            MobileLoginResponse resp = buildLoginResponse(as, token, refreshToken, account.isMustChangePassword());
            log.info("Mobile login (single): academicStudentId={}", as.getId());
            return ResponseEntity.ok(ApiResponse.success("Login successful", resp));
        }

        // 4b. Multiple children → temp session token + child list
        String tempToken = parentSessionStore.createToken(request.getMobile());
        List<ChildSummaryDto> children = students.stream()
                .map(this::toChildSummary)
                .collect(Collectors.toList());

        MultiChildResponse multiResp = MultiChildResponse.builder()
                .loginType("MULTI_CHILD")
                .tempToken(tempToken)
                .mustChangePassword(account.isMustChangePassword())
                .children(children)
                .build();

        log.info("Mobile login (multi-child): mobile={}, count={}", request.getMobile(), children.size());
        return ResponseEntity.ok(ApiResponse.success("Multiple children found — please select", multiResp));
    }

    // ── POST /api/v1/auth/select-child ────────────────────────────────────────

    @PostMapping("/select-child")
    public ResponseEntity<ApiResponse<MobileLoginResponse>> selectChild(
            @Valid @RequestBody SelectChildRequest request) {
        log.info("Inside selectChild");

        // 1. Validate temp token → get mobile
        String mobile = parentSessionStore.validateAndConsume(request.getTempToken());
        if (mobile == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Session expired — please login again"));
        }

        // 2. Find children via same SiblingGroup-first strategy
        List<AcademicStudent> students = academicStudentService.findSiblingsByMobile(mobile);
        if (students.isEmpty()) {
            FamilyAccount parentAccount = familyAccountService.findActive(mobile).orElse(null);
            students = parentAccount != null
                    ? academicStudentService.findActiveByFamilyAccount(parentAccount)
                    : List.of();
        }
        AcademicStudent chosen = students.stream()
                .filter(s -> s.getId().equals(request.getAcademicStudentId()))
                .findFirst().orElse(null);

        if (chosen == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Invalid child selection"));
        }

        // 3. Lookup mustChangePassword from FamilyAccount
        boolean mustChange = familyAccountService.findByMobile(mobile)
                .map(FamilyAccount::isMustChangePassword)
                .orElse(false);

        String token = buildJwt(chosen);
        String refreshToken = refreshTokenService.issueToken(chosen);
        MobileLoginResponse resp = buildLoginResponse(chosen, token, refreshToken, mustChange);

        log.info("Child selected: academicStudentId={}", chosen.getId());
        return ResponseEntity.ok(ApiResponse.success("Login successful", resp));
    }

    // ── POST /api/v1/auth/change-password ────────────────────────────────────
    // Requires a valid student JWT (so the parent is already authenticated)

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestBody Map<String, String> body,
            jakarta.servlet.http.HttpServletRequest request) {
        log.info("Inside changePassword");

        // Derive identity from the authenticated JWT — never trust a client-supplied header.
        // JwtAuthenticationFilter stores academicStudentId in request attribute on every call.
        Long academicStudentId = (Long) request.getAttribute("academicStudentId");
        if (academicStudentId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated"));
        }

        String currentPassword = body.get("currentPassword");
        String newPassword     = body.get("newPassword");

        if (currentPassword == null || newPassword == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("currentPassword and newPassword are required"));
        }

        // JWT → AcademicStudent → Student → FamilyAccount (no mobile header needed)
        var optAs = academicStudentService.findById(academicStudentId);
        if (optAs.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Student record not found"));
        }

        FamilyAccount account = optAs.get().getStudent().getFamilyAccount();
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No family account linked to this student — contact school admin"));
        }

        String error = familyAccountService.changePassword(account, currentPassword, newPassword);
        if (error != null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(error));
        }

        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    // ── POST /api/v1/auth/refresh ─────────────────────────────────────────────
    // No JWT required — the whole point is to get a new access token once the
    // old one has expired. Trades a valid, not-yet-used refresh token for a
    // new access token + a new (rotated) refresh token.

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(
            @RequestBody Map<String, String> body) {
        log.info("Inside refresh");

        String rawRefreshToken = body.get("refreshToken");
        Optional<MobileRefreshTokenService.RotationResult> result = refreshTokenService.rotate(rawRefreshToken);

        if (result.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Session expired — please login again"));
        }

        AcademicStudent as = result.get().academicStudent;
        String newAccessToken = buildJwt(as);

        Map<String, Object> data = Map.of(
                "token", newAccessToken,
                "refreshToken", result.get().newRawRefreshToken,
                "tokenType", "Bearer"
        );

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ── POST /api/v1/auth/logout ──────────────────────────────────────────────
    // Requires a valid access token (proves who's logging out). Revokes the
    // refresh token server-side immediately instead of relying on it expiring
    // naturally — important for a lost/shared device. If no refreshToken is
    // supplied in the body, every active session for this student is revoked.

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) Map<String, String> body,
            jakarta.servlet.http.HttpServletRequest request) {
        log.info("Inside logout");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");
        String rawRefreshToken = body != null ? body.get("refreshToken") : null;

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        } else if (academicStudentId != null) {
            refreshTokenService.revokeAllForStudent(academicStudentId);
        }

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── POST /api/v1/auth/switch-child ───────────────────────────────────────
    // Requires a valid student JWT. Switches the active child without re-entering password.
    // The current JWT proves parent identity → we find siblings → issue new JWT for chosen child.

    @PostMapping("/switch-child")
    public ResponseEntity<ApiResponse<MobileLoginResponse>> switchChild(
            @RequestBody Map<String, Object> body,
            jakarta.servlet.http.HttpServletRequest request) {
        log.info("Inside switchChild");

        Long currentAcademicStudentId = (Long) request.getAttribute("academicStudentId");
        if (currentAcademicStudentId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated"));
        }

        Object idObj = body.get("academicStudentId");
        if (idObj == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("academicStudentId is required"));
        }
        Long newAcademicStudentId = ((Number) idObj).longValue();

        // Find current student → get parent mobile via FamilyAccount or mobile1
        var currentStudentOpt = academicStudentService.findById(currentAcademicStudentId);
        if (currentStudentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Current student not found"));
        }

        String mobile = currentStudentOpt.get().getStudent().getMobile1();
        if (mobile == null || mobile.isBlank()) {
            // Try FamilyAccount FK
            FamilyAccount fa = currentStudentOpt.get().getStudent().getFamilyAccount();
            if (fa != null) mobile = fa.getMobile();
        }
        if (mobile == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Cannot determine parent mobile"));
        }

        // Find all siblings for this mobile
        List<AcademicStudent> siblings = academicStudentService.findSiblingsByMobile(mobile);
        if (siblings.isEmpty()) {
            FamilyAccount fa = familyAccountService.findActive(mobile).orElse(null);
            siblings = fa != null ? academicStudentService.findActiveByFamilyAccount(fa) : List.of();
        }

        // Verify the requested child belongs to the same family
        AcademicStudent chosen = siblings.stream()
                .filter(s -> s.getId().equals(newAcademicStudentId))
                .findFirst().orElse(null);

        if (chosen == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Invalid child selection — not linked to your account"));
        }

        boolean mustChange = familyAccountService.findActive(mobile)
                .map(FamilyAccount::isMustChangePassword)
                .orElse(false);

        // Revoke the previous child's active sessions for cleanliness — switching
        // child is effectively a new login for a different identity, so the old
        // refresh-token rows would otherwise sit around unused until they expire.
        refreshTokenService.revokeAllForStudent(currentAcademicStudentId);

        String token = buildJwt(chosen);
        String refreshToken = refreshTokenService.issueToken(chosen);
        MobileLoginResponse resp = buildLoginResponse(chosen, token, refreshToken, mustChange);

        log.info("Child switched: from={} to={}", currentAcademicStudentId, chosen.getId());
        return ResponseEntity.ok(ApiResponse.success("Switched successfully", resp));
    }

    // ── GET /api/v1/auth/siblings ─────────────────────────────────────────────
    // Returns list of all siblings for the currently logged-in parent.
    // Used by the Switch Student screen to populate the picker.

    @GetMapping("/siblings")
    public ResponseEntity<ApiResponse<List<ChildSummaryDto>>> getSiblings(
            jakarta.servlet.http.HttpServletRequest request) {
        log.info("Inside getSiblings");

        Long currentAcademicStudentId = (Long) request.getAttribute("academicStudentId");
        if (currentAcademicStudentId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated"));
        }

        var currentStudentOpt = academicStudentService.findById(currentAcademicStudentId);
        if (currentStudentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Student not found"));
        }

        String mobile = currentStudentOpt.get().getStudent().getMobile1();
        if (mobile == null || mobile.isBlank()) {
            FamilyAccount fa = currentStudentOpt.get().getStudent().getFamilyAccount();
            if (fa != null) mobile = fa.getMobile();
        }

        if (mobile == null) return ResponseEntity.ok(ApiResponse.success(List.of()));

        List<AcademicStudent> siblings = academicStudentService.findSiblingsByMobile(mobile);
        if (siblings.isEmpty()) {
            FamilyAccount fa = familyAccountService.findActive(mobile).orElse(null);
            siblings = fa != null ? academicStudentService.findActiveByFamilyAccount(fa) : List.of();
        }

        List<ChildSummaryDto> result = siblings.stream()
                .map(this::toChildSummary)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildJwt(AcademicStudent as) {
        return jwtTokenProvider.generateToken(
                as.getStudent().getUserEntity().getUsername(),
                as.getId(),
                as.getSchool().getId(),
                as.getAcademicYear().getId(),
                as.getStudent().getStudentName());
    }

    private MobileLoginResponse buildLoginResponse(AcademicStudent as,
                                                    String token,
                                                    String refreshToken,
                                                    boolean mustChangePassword) {
        return MobileLoginResponse.builder()
                .loginType("SINGLE")
                .token(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .mustChangePassword(mustChangePassword)
                .academicStudentId(as.getId())
                .studentName(as.getStudent().getStudentName())
                .fatherName(as.getStudent().getFatherName())
                .classSrNo(as.getClassSrNo())
                .rollNo(as.getRollNo())
                .gradeName(as.getGrade().getGradeName())
                .sectionName(as.getSection().getSectionName())
                .mediumName(as.getMedium().getMediumName())
                .schoolName(as.getSchool().getSchoolName())
                .schoolId(as.getSchool().getId())
                .academicYearId(as.getAcademicYear().getId())
                .academicYearName(as.getAcademicYear().getSessionFormat())
                .profilePicUrl(as.getStudent().getPic() != null
                        ? "/sms/api/v1/student/pic/" + as.getStudent().getPic() : null)
                .build();
    }

    private ChildSummaryDto toChildSummary(AcademicStudent as) {
        return ChildSummaryDto.builder()
                .academicStudentId(as.getId())
                .studentName(as.getStudent().getStudentName())
                .gradeName(as.getGrade().getGradeName())
                .sectionName(as.getSection().getSectionName())
                .mediumName(as.getMedium().getMediumName())
                .classSrNo(as.getClassSrNo())
                .rollNo(as.getRollNo())
                .profilePicUrl(as.getStudent().getPic() != null
                        ? "/sms/api/v1/student/pic/" + as.getStudent().getPic() : null)
                .build();
    }
}
