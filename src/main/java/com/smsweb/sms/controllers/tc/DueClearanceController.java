package com.smsweb.sms.controllers.tc;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.dto.tc.ClearDuesRequest;
import com.smsweb.sms.dto.tc.ReceiptFilter;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.tc.DueClearanceReceipt;
import com.smsweb.sms.services.tc.DueClearanceReceiptService;
import com.smsweb.sms.services.tc.StudentExitService;
import com.smsweb.sms.services.tc.TcConfigService;
import com.smsweb.sms.services.tc.TcValidationException;
import com.smsweb.sms.services.users.UserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Due Clearance Receipts: clear the dues of an earlier-session exit, the receipt register, the
 * printable receipt and the admin-only void. Thin by design: every rule lives in
 * {@link DueClearanceReceiptService}. The school comes from the session, never from request data.
 */
@Controller
@RequestMapping("/tc")
public class DueClearanceController extends TcBaseController {

    private static final Logger log = LoggerFactory.getLogger(DueClearanceController.class);

    private final DueClearanceReceiptService receiptService;

    public DueClearanceController(DueClearanceReceiptService receiptService,
                                  TcConfigService tcConfigService,
                                  UserService userService) {
        super(tcConfigService, userService);
        this.receiptService = receiptService;
    }

    @PostMapping("/exits/{uuid}/clear-dues")
    @CheckAccess(screen = "TC_DUES", type = AccessType.CREATE)
    public String clearDues(@PathVariable UUID uuid,
                            @RequestParam(required = false) BigDecimal amountPaid,
                            @RequestParam(required = false) BigDecimal discountAmount,
                            @RequestParam(required = false) String discountReason,
                            @RequestParam(required = false) String paymentMode,
                            @RequestParam(required = false) String referenceNo,
                            @RequestParam(required = false) String remark,
                            @RequestParam(required = false) String submissionToken,
                            HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside clear dues {}", uuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        ClearDuesRequest req = new ClearDuesRequest();
        req.setAmountPaid(amountPaid);
        req.setDiscountAmount(discountAmount);
        req.setDiscountReason(discountReason);
        req.setPaymentMode(paymentMode);
        req.setReferenceNo(referenceNo);
        req.setRemark(remark);
        req.setSubmissionToken(submissionToken);
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        try {
            DueClearanceReceipt r = receiptService.createLedgerReceipt(uuid, req, school, academicYear, currentUser());
            ra.addFlashAttribute("success", "Dues cleared. Receipt " + r.getReceiptNo() + " saved.");
            ra.addFlashAttribute("newReceiptUuid", r.getUuid().toString());
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not clear dues of exit {}", uuid, e);
            ra.addFlashAttribute("error", "Could not save the receipt. Please check the receipt list before trying again.");
        }
        return "redirect:/tc/exits/" + uuid;
    }

    /** Repair: creates the Due Clearance Receipt for a fee saved on the fee screen that has none. */
    @PostMapping("/exits/{uuid}/sync-receipts")
    @CheckAccess(screen = "TC_DUES", type = AccessType.CREATE)
    public String syncReceipts(@PathVariable UUID uuid, HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside sync receipts {}", uuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        try {
            int made = receiptService.createMissingReceipts(uuid, school, academicYear, currentUser());
            ra.addFlashAttribute("success", made == 0 ? "No missing receipts were found."
                    : made + " receipt(s) created.");
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not create the missing receipts of exit {}", uuid, e);
            ra.addFlashAttribute("error", "Could not create the receipt. Please try again.");
        }
        return "redirect:/tc/exits/" + uuid;
    }

    @GetMapping("/receipts")
    @CheckAccess(screen = "TC_DUES", type = AccessType.VIEW)
    public String register(@ModelAttribute("filter") ReceiptFilter filter, BindingResult binding,
                           HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside receipt register");
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        ReceiptFilter f = binding.hasErrors() ? new ReceiptFilter() : filter;
        if (binding.hasErrors()) {
            model.addAttribute("filter", f);
            model.addAttribute("error", "Some filters were not understood and were ignored.");
        }
        model.addAttribute("rows", receiptService.search(f, school));
        model.addAttribute("totals", receiptService.totals(f, school));
        model.addAttribute("isAdmin", StudentExitService.isAdmin());
        return "tc/receipt-register";
    }

    @GetMapping("/receipts/{uuid}/print")
    @CheckAccess(screen = "TC_DUES", type = AccessType.VIEW)
    public String print(@PathVariable UUID uuid, HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside receipt print {}", uuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        try {
            model.addAttribute("r", receiptService.printData(uuid, school));
            return "tc/receipt-print";
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not open receipt {}", uuid, e);
            ra.addFlashAttribute("error", "Could not open the receipt. Please try again.");
        }
        return "redirect:/tc/receipts";
    }

    @PostMapping("/receipts/{uuid}/void")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
    @CheckAccess(screen = "TC_DUES", type = AccessType.VIEW)
    public String voidReceipt(@PathVariable UUID uuid,
                              @RequestParam(required = false) String reason,
                              HttpSession session, Model model, RedirectAttributes ra) {
        log.info("Inside void receipt {}", uuid);
        School school = (School) session.getAttribute("school");
        String blocked = featureBlock(school, ra);
        if (blocked != null) {
            return blocked;
        }
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
        try {
            DueClearanceReceipt r = receiptService.voidReceipt(uuid, reason, school, academicYear, currentUser());
            ra.addFlashAttribute("success", "Receipt " + r.getReceiptNo() + " voided. The amount is pending again.");
            return "redirect:/tc/exits/" + r.getExit().getUuid();
        } catch (TcValidationException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Could not void receipt {}", uuid, e);
            ra.addFlashAttribute("error", "Could not void the receipt. Please try again.");
        }
        return "redirect:/tc/receipts";
    }
}
