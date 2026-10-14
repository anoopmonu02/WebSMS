package com.smsweb.sms.services.student;

import com.smsweb.sms.models.student.SiblingGroup;
import com.smsweb.sms.models.student.SiblingGroupStudent;
import com.smsweb.sms.models.student.StudentDiscount;
import com.smsweb.sms.repositories.student.SiblingGroupRepository;
import com.smsweb.sms.repositories.student.SiblingGroupStudentRepository;
import com.smsweb.sms.repositories.student.StudentDiscountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only data for Fee Report > Sibling Report: one row per Active sibling group of a school + academic
 * year, with its members and the member(s) who hold the Sibling Discount. Uses three set-based queries
 * (groups, members, discounts) regardless of the number of groups.
 */
@Service
public class SiblingReportService {

    private static final String SIBLING_DISCOUNT_NAME = "Sibling Discount";

    private final SiblingGroupRepository siblingGroupRepository;
    private final SiblingGroupStudentRepository siblingGroupStudentRepository;
    private final StudentDiscountRepository studentDiscountRepository;

    public SiblingReportService(SiblingGroupRepository siblingGroupRepository,
                                SiblingGroupStudentRepository siblingGroupStudentRepository,
                                StudentDiscountRepository studentDiscountRepository) {
        this.siblingGroupRepository = siblingGroupRepository;
        this.siblingGroupStudentRepository = siblingGroupStudentRepository;
        this.studentDiscountRepository = studentDiscountRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSiblingReport(Long schoolId, Long academicYearId) {
        List<SiblingGroup> groups = new ArrayList<>(
                siblingGroupRepository.findAllBySchool_IdAndAcademicYear_IdAndStatus(schoolId, academicYearId, "Active"));
        groups.sort(Comparator.comparing(SiblingGroup::getGroupName, String.CASE_INSENSITIVE_ORDER));

        // members per group
        Map<Long, List<SiblingGroupStudent>> membersByGroup = new HashMap<>();
        for (SiblingGroupStudent m : siblingGroupStudentRepository.findAllActiveGroupMembers(schoolId, academicYearId)) {
            membersByGroup.computeIfAbsent(m.getSiblingGroup().getId(), k -> new ArrayList<>()).add(m);
        }

        // active sibling discounts per academic student
        Map<Long, StudentDiscount> discountByStudent = new HashMap<>();
        for (StudentDiscount sd : studentDiscountRepository.findAllActiveByHeadName(schoolId, academicYearId, SIBLING_DISCOUNT_NAME)) {
            discountByStudent.put(sd.getAcademicStudent().getId(), sd);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SiblingGroup group : groups) {
            List<SiblingGroupStudent> members = membersByGroup.getOrDefault(group.getId(), new ArrayList<>());
            List<String> memberNames = new ArrayList<>();
            List<String> holderNames = new ArrayList<>();
            List<String> holderSrNos = new ArrayList<>();
            Set<String> assignedBy = new LinkedHashSet<>();
            List<String> fatherNames = new ArrayList<>();   // distinct (case-insensitive), in member order
            Set<String> fatherKeys = new HashSet<>();
            for (SiblingGroupStudent m : members) {
                String srNo = nullToEmpty(m.getAcademicStudent().getClassSrNo()).trim();
                String name = nullToEmpty(m.getAcademicStudent().getStudent().getStudentName());
                memberNames.add(srNo.isEmpty() ? name : name + " (" + srNo + ")");
                String father = nullToEmpty(m.getAcademicStudent().getStudent().getFatherName()).trim();
                if (!father.isEmpty() && fatherKeys.add(father.toLowerCase())) {
                    fatherNames.add(father);
                }
                StudentDiscount sd = discountByStudent.get(m.getAcademicStudent().getId());
                if (sd != null) {
                    holderNames.add(nullToEmpty(m.getAcademicStudent().getStudent().getStudentName()));
                    holderSrNos.add(nullToEmpty(m.getAcademicStudent().getClassSrNo()));
                    if (sd.getCreatedBy() != null && sd.getCreatedBy().getDisplayName() != null) {
                        assignedBy.add(sd.getCreatedBy().getDisplayName());
                    }
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("groupName", nullToEmpty(group.getGroupName()));
            row.put("groupStudents", String.join(", ", memberNames));
            row.put("fatherName", String.join(", ", fatherNames));
            row.put("studentCount", members.size());
            row.put("srNo", String.join(", ", holderSrNos));
            row.put("discountStudent", String.join(", ", holderNames));
            row.put("assignedBy", String.join(", ", assignedBy));
            rows.add(row);
        }
        return rows;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
