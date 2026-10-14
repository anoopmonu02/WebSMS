package com.smsweb.sms.services.student;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.SiblingGroup;
import com.smsweb.sms.models.student.SiblingGroupStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.student.StudentDiscount;
import com.smsweb.sms.repositories.student.SiblingGroupRepository;
import com.smsweb.sms.repositories.student.SiblingGroupStudentRepository;
import com.smsweb.sms.repositories.student.StudentDiscountRepository;
import com.smsweb.sms.services.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NEW service (feature: removing a student who belongs to a sibling group).
 *
 * When a student is removed, the sibling group he/she belongs to no longer holds together, so it is
 * dissolved with the same final action the Sibling Group screen already uses (the group row is deleted,
 * its member rows go with it). Optionally a NEW group is re-formed from the students who remain, carrying
 * a reference to the old group in its description.
 *
 * Rules:
 *  - A new group is only created when at least MIN_GROUP_SIZE active students remain (the same
 *    "3 or more siblings" rule the Assign Sibling Discount screen applies) and none of them is already
 *    in another active group.
 *  - Sibling Discount rows: the removed student's (and any already-inactive member's) discount is stopped.
 *    Remaining students keep their Sibling Discount only when a new group is created for them; if no new
 *    group exists their Sibling Discount is stopped too, because no qualifying group is left.
 *    Only the "Sibling Discount" head is touched, other discounts are never changed.
 *
 * Everything runs inside the caller's transaction (StudentRemovalService), so the student removal and the
 * group changes commit or roll back together.
 */
@Service
public class SiblingGroupDissolutionService {

    private static final Logger log = LoggerFactory.getLogger(SiblingGroupDissolutionService.class);

    /** Same rule as the Assign Sibling Discount screen: at least 3 siblings in a group. */
    public static final int MIN_GROUP_SIZE = 3;
    private static final String SIBLING_DISCOUNT_NAME = "Sibling Discount";
    private static final int MAX_DESCRIPTION = 500;

    private final SiblingGroupRepository siblingGroupRepository;
    private final SiblingGroupStudentRepository siblingGroupStudentRepository;
    private final StudentDiscountRepository studentDiscountRepository;
    private final SiblingGroupService siblingGroupService;
    private final UserService userService;

    @Autowired
    public SiblingGroupDissolutionService(SiblingGroupRepository siblingGroupRepository,
                                          SiblingGroupStudentRepository siblingGroupStudentRepository,
                                          StudentDiscountRepository studentDiscountRepository,
                                          SiblingGroupService siblingGroupService,
                                          UserService userService) {
        this.siblingGroupRepository = siblingGroupRepository;
        this.siblingGroupStudentRepository = siblingGroupStudentRepository;
        this.studentDiscountRepository = studentDiscountRepository;
        this.siblingGroupService = siblingGroupService;
        this.userService = userService;
    }

    /** What happened to one dissolved group. */
    public static class Outcome {
        public String oldGroupName;
        public String newGroupName;                 // null when no new group was created
        public List<String> newGroupMembers = new ArrayList<>();
        public String notCreatedReason;             // set only when a new group was requested but could not be created
    }

    @Transactional(readOnly = true)
    public boolean hasActiveGroup(Long studentId) {
        return !activeGroupsOf(studentId).isEmpty();
    }

    /** Read-only preview shown in the confirmation popup before anything is changed. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> previewForStudent(Long studentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SiblingGroup group : activeGroupsOf(studentId)) {
            List<SiblingGroupStudent> members = siblingGroupStudentRepository.findAllBySiblingGroup(group);
            Set<Long> holderIds = siblingDiscountHolderIds(group);
            Set<Long> remainingStudentIds = new LinkedHashSet<>();
            List<Map<String, Object>> memberList = new ArrayList<>();
            for (SiblingGroupStudent m : members) {
                AcademicStudent a = m.getAcademicStudent();
                boolean removed = a.getStudent().getId().equals(studentId);
                boolean eligible = !removed && isEligibleMember(a);
                if (eligible) {
                    remainingStudentIds.add(a.getStudent().getId());
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", a.getStudent().getStudentName());
                row.put("gradeSection", gradeSection(a));
                row.put("schoolName", a.getSchool() != null ? a.getSchool().getSchoolName() : "");
                row.put("removed", removed);
                row.put("inactive", !removed && !eligible);
                row.put("hasSiblingDiscount", holderIds.contains(a.getId()));
                memberList.add(row);
            }
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("groupId", group.getId());
            g.put("groupName", group.getGroupName());
            g.put("schoolName", group.getSchool() != null ? group.getSchool().getSchoolName() : "");
            g.put("totalMembers", members.size());
            g.put("remaining", remainingStudentIds.size());
            g.put("minRequired", MIN_GROUP_SIZE);
            g.put("canRecreate", remainingStudentIds.size() >= MIN_GROUP_SIZE);
            g.put("members", memberList);
            result.add(g);
        }
        return result;
    }

    /**
     * Dissolves every active sibling group of the (already removed) student and, when requested and the rule
     * allows, re-forms a new group from the remaining students. Must run inside a transaction.
     */
    @Transactional
    public List<Outcome> dissolveForRemovedStudent(Long studentId, String removedStudentName, boolean createNewGroup) {
        List<Outcome> outcomes = new ArrayList<>();
        for (SiblingGroup candidate : activeGroupsOf(studentId)) {
            // Lock the group row so nobody assigns a discount / edits it while it is being dissolved.
            SiblingGroup group = siblingGroupRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (group == null || !"Active".equalsIgnoreCase(group.getStatus())) {
                continue;
            }

            // ---- snapshot before deleting ----
            List<SiblingGroupStudent> members = siblingGroupStudentRepository.findAllBySiblingGroup(group);
            Map<Long, AcademicStudent> remaining = new LinkedHashMap<>();   // keyed by physical student id
            List<String> allNames = new ArrayList<>();
            for (SiblingGroupStudent m : members) {
                AcademicStudent a = m.getAcademicStudent();
                allNames.add(a.getStudent().getStudentName());
                Long sid = a.getStudent().getId();
                if (!sid.equals(studentId) && isEligibleMember(a)) {
                    remaining.putIfAbsent(sid, a);
                }
            }
            List<StudentDiscount> siblingDiscounts = studentDiscountRepository.findActiveByHeadNameForGroup(
                    group.getSchool().getId(), group.getAcademicYear().getId(), SIBLING_DISCOUNT_NAME, group.getId());

            Outcome outcome = new Outcome();
            outcome.oldGroupName = group.getGroupName();
            String oldName = group.getGroupName();
            Long oldId = group.getId();
            var school = group.getSchool();
            var academicYear = group.getAcademicYear();

            // ---- final action of the existing Sibling Group screen: delete the group (members go with it) ----
            siblingGroupRepository.delete(group);
            siblingGroupRepository.flush();

            // ---- optional: re-form a new group from the remaining students ----
            SiblingGroup newGroup = null;
            if (createNewGroup) {
                if (remaining.size() < MIN_GROUP_SIZE) {
                    outcome.notCreatedReason = "Sibling rule not fulfilled (minimum " + MIN_GROUP_SIZE
                            + " students required, only " + remaining.size() + " remaining), so a new group cannot be created. Please contact admin.";
                } else {
                    String check = siblingGroupService.validateExistingGroupStudent(new ArrayList<>(remaining.values()), school, academicYear);
                    if (check != null && !check.trim().isEmpty()) {
                        outcome.notCreatedReason = "A new group cannot be created because " + stripPrefix(check) + " Please contact admin.";
                    } else {
                        newGroup = createReformedGroup(oldName, oldId, allNames, removedStudentName, school, academicYear, remaining.values());
                        outcome.newGroupName = newGroup.getGroupName();
                        outcome.newGroupMembers = remaining.values().stream()
                                .map(a -> a.getStudent().getStudentName()).collect(Collectors.toList());
                    }
                }
            }

            // ---- Sibling Discount: keep it only for remaining students of a newly created group ----
            Set<Long> keepAcademicIds = newGroup == null ? Collections.emptySet()
                    : remaining.values().stream().map(AcademicStudent::getId).collect(Collectors.toSet());
            List<StudentDiscount> toStop = new ArrayList<>();
            for (StudentDiscount sd : siblingDiscounts) {
                if (!keepAcademicIds.contains(sd.getAcademicStudent().getId())) {
                    sd.setStatus("Inactive");
                    toStop.add(sd);
                }
            }
            if (!toStop.isEmpty()) {
                studentDiscountRepository.saveAll(toStop);
            }
            log.info("Sibling group '{}' (id={}) dissolved after removal of student id={}; newGroup={}; discountsStopped={}",
                    oldName, oldId, studentId, newGroup != null ? newGroup.getGroupName() : "none", toStop.size());
            outcomes.add(outcome);
        }
        return outcomes;
    }

    private SiblingGroup createReformedGroup(String oldName, Long oldId, List<String> oldMembers, String removedName,
                                             com.smsweb.sms.models.admin.School school,
                                             com.smsweb.sms.models.admin.AcademicYear academicYear,
                                             Collection<AcademicStudent> members) {
        String base = oldName == null ? "Group" : (oldName.length() > 80 ? oldName.substring(0, 80) : oldName);
        String name = base + " (Reformed)";
        int n = 2;
        while (siblingGroupRepository.existsByGroupNameAndSchool_IdAndAcademicYear_Id(name, school.getId(), academicYear.getId())) {
            name = base + " (Reformed " + n++ + ")";
        }
        String description = "Re-formed on " + new SimpleDateFormat("dd-MMM-yyyy").format(new Date())
                + " from group '" + oldName + "' (id " + oldId + ", " + oldMembers.size() + " students: "
                + String.join(", ", oldMembers) + ") after removal of student '" + removedName + "'.";
        if (description.length() > MAX_DESCRIPTION) {
            description = description.substring(0, MAX_DESCRIPTION - 3) + "...";
        }
        SiblingGroup ng = new SiblingGroup();
        ng.setGroupName(name);
        ng.setDescription(description);
        ng.setSchool(school);
        ng.setAcademicYear(academicYear);
        ng.setCreatedBy(userService.getLoggedInUser());
        ng = siblingGroupRepository.save(ng);
        for (AcademicStudent a : members) {
            SiblingGroupStudent sgs = new SiblingGroupStudent();
            sgs.setAcademicStudent(a);
            sgs.setSiblingGroup(ng);
            siblingGroupStudentRepository.save(sgs);
        }
        return ng;
    }

    // ---------------- helpers ----------------

    /** Distinct active groups the physical student is a member of (any branch / year). */
    private List<SiblingGroup> activeGroupsOf(Long studentId) {
        Map<Long, SiblingGroup> groups = new LinkedHashMap<>();
        for (SiblingGroupStudent sgs : siblingGroupStudentRepository.findActiveGroupsByStudentId(studentId)) {
            groups.putIfAbsent(sgs.getSiblingGroup().getId(), sgs.getSiblingGroup());
        }
        return new ArrayList<>(groups.values());
    }

    private Set<Long> siblingDiscountHolderIds(SiblingGroup group) {
        return studentDiscountRepository.findActiveByHeadNameForGroup(
                        group.getSchool().getId(), group.getAcademicYear().getId(), SIBLING_DISCOUNT_NAME, group.getId())
                .stream().map(sd -> sd.getAcademicStudent().getId()).collect(Collectors.toSet());
    }

    /** A member counts toward a (new) group only while still enrolled: active, not migrated out, student active. */
    private boolean isEligibleMember(AcademicStudent a) {
        return AcademicStudent.STATUS_ACTIVE.equalsIgnoreCase(a.getStatus())
                && !Boolean.TRUE.equals(a.getIsMigrated())
                && a.getStudent() != null
                && Student.STATUS_ACTIVE.equalsIgnoreCase(a.getStudent().getStatus());
    }

    private String gradeSection(AcademicStudent a) {
        String grade = a.getGrade() != null ? a.getGrade().getGradeName() : "";
        String section = a.getSection() != null ? a.getSection().getSectionName() : "";
        return section.isEmpty() ? grade : grade + " - " + section;
    }

    private String stripPrefix(String msg) {
        int i = msg.indexOf(':');
        String s = i >= 0 ? msg.substring(i + 1) : msg;
        s = s.trim();
        return s.endsWith(".") ? s : s + ".";
    }
}
