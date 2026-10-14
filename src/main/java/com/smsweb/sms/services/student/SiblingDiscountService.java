package com.smsweb.sms.services.student;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.DiscountClassMap;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.SiblingGroup;
import com.smsweb.sms.models.student.SiblingGroupStudent;
import com.smsweb.sms.models.student.StudentDiscount;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import com.smsweb.sms.repositories.admin.DiscountclassmapRepository;
import com.smsweb.sms.repositories.admin.SchoolRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.SiblingDiscountRepository;
import com.smsweb.sms.repositories.student.SiblingGroupRepository;
import com.smsweb.sms.repositories.student.SiblingGroupStudentRepository;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.repositories.student.StudentDiscountRepository;
import com.smsweb.sms.services.admin.SiblingDiscountConfigService;
import com.smsweb.sms.services.users.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class SiblingDiscountService {
    private static final Logger log = LoggerFactory.getLogger(SiblingDiscountService.class);

    private SiblingDiscountRepository siblingDiscountRepository;
    private final AcademicyearRepository academicyearRepository;
    private final SchoolRepository schoolRepository;
    private final AcademicStudentRepository academicStudentRepository;
    private final SiblingGroupStudentRepository siblingGroupStudentRepository;
    private final DiscountclassmapRepository discountclassmapRepository;
    private final StudentDiscountRepository studentDiscountRepository;
    private final UserService userService;
    private final SiblingGroupRepository siblingGroupRepository;
    private final SiblingDiscountConfigService siblingDiscountConfigService;

    /** Discount head name used throughout the sibling-discount flow. */
    private static final String SIBLING_DISCOUNT_NAME = "Sibling Discount";

    @Autowired
    public SiblingDiscountService(SiblingDiscountRepository siblingDiscountRepository, AcademicyearRepository academicyearRepository, SchoolRepository schoolRepository, AcademicStudentRepository academicStudentRepository, SiblingGroupStudentRepository siblingGroupStudentRepository, DiscountclassmapRepository discountclassmapRepository, StudentDiscountRepository studentDiscountRepository, UserService userService,
                                 SiblingGroupRepository siblingGroupRepository, SiblingDiscountConfigService siblingDiscountConfigService){
        this.siblingDiscountRepository = siblingDiscountRepository;
        this.academicyearRepository = academicyearRepository;
        this.schoolRepository = schoolRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.siblingGroupStudentRepository = siblingGroupStudentRepository;
        this.discountclassmapRepository = discountclassmapRepository;
        this.studentDiscountRepository = studentDiscountRepository;
        this.userService = userService;
        this.siblingGroupRepository = siblingGroupRepository;
        this.siblingDiscountConfigService = siblingDiscountConfigService;
    }

    @Transactional
    public Map save(Map<String, String[]> paramsMap, School school, AcademicYear academicYear){
        log.info("Inside save");
        Map<String, String> resultMap = new HashMap();
        try{
            if(paramsMap!=null && !paramsMap.isEmpty()){
                Long groupId = null;
                Long stuId = null;
                for (Map.Entry<String, String[]> entry : paramsMap.entrySet()) {
                    String key = entry.getKey();
                    String[] values = entry.getValue();
                    for (String value : values) {
                        if(key.equalsIgnoreCase("grp") && value!=null && value!=""){
                            groupId = Long.parseLong(value);
                        } else if(key.equalsIgnoreCase("assignedstu") && value!=null && value!=""){
                            stuId = Long.parseLong(value);
                        }
                    }
                }
                if(stuId!=null && groupId!=null){
                    // Lock the group row first so two simultaneous saves for the same group are serialised
                    // (the limit check below must see the other save's result).
                    Optional<SiblingGroup> groupOpt = siblingGroupRepository.findByIdForUpdate(groupId);
                    if(groupOpt.isEmpty() || !isGroupUsable(groupOpt.get(), school, academicYear)){
                        resultMap.put("error", "Sibling group not found. Please refresh the page and try again.");
                        return resultMap;
                    }
                    SiblingGroup group = groupOpt.get();

                    Optional<AcademicStudent> student = academicStudentRepository.findById(stuId);
                    if(student.isPresent()){
                        String blockMsg = validateSiblingDiscountEligibility(student.get(), group, school, academicYear);
                        if(blockMsg != null){
                            resultMap.put("error", blockMsg);
                            return resultMap;
                        }
                        Optional<DiscountClassMap> discountClassMap = discountclassmapRepository.findByDiscounthead_DiscountNameAndAcademicYear_IdAndSchool_IdAndGrade_IdAndMedium_Id("Sibling Discount", academicYear.getId(), school.getId(), student.get().getGrade().getId(), student.get().getMedium().getId());
                        if(discountClassMap.isPresent()){
                            // A student can hold only one row per discount head/year/school (unique constraint), so a row
                            // left Inactive by an earlier group (dissolved/removed) is re-activated instead of inserting a new one.
                            Long headId = discountClassMap.get().getDiscounthead().getId();
                            StudentDiscount studentDiscount = studentDiscountRepository
                                    .findAllBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(school.getId(), academicYear.getId(), student.get().getId())
                                    .stream()
                                    .filter(sd -> sd.getDiscounthead() != null && headId.equals(sd.getDiscounthead().getId()))
                                    .findFirst()
                                    .orElse(null);
                            if(studentDiscount == null){
                                studentDiscount = new StudentDiscount();
                                studentDiscount.setAcademicStudent(student.get());
                                studentDiscount.setSchool(school);
                                studentDiscount.setAcademicYear(academicYear);
                                studentDiscount.setDiscounthead(discountClassMap.get().getDiscounthead());
                                studentDiscount.setCreatedBy(userService.getLoggedInUser());
                            }
                            studentDiscount.setDescription("Sibling Discount Mapped");
                            studentDiscount.setStatus("Active");
                            studentDiscountRepository.save(studentDiscount);
                            resultMap.put("DISCOUNT_SAVED", "Discount saved for Student: "+student.get().getStudent().getStudentName());
                        } else{
                            resultMap.put("NO_DISCOUNT_FOUND","Discount not mapped with Grade: "+student.get().getGrade().getGradeName());
                        }
                    } else{
                        resultMap.put("NO_STU_FOUND","Student not found");
                    }
                } else{
                    resultMap.put("NO_STU_OR_GRP_FOUND","Fail to process request");
                }
            }
        }catch(Exception e){
            log.error("Error while saving sibling discount", e);
            resultMap.put("error", "Unable to save the sibling discount right now. Please try again.");
        }
        return resultMap;
    }

    /**
     * Read-only overview used by the Assign Sibling Discount screen: how many sibling discounts the
     * school allows per group, how many are already assigned in this group, and - per group member -
     * the names of the Active discounts they hold and whether one of them is the Sibling Discount.
     * Same definitions as the save-time check, so the screen and the server always agree.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getGroupDiscountOverview(Long groupId, School school, AcademicYear academicYear){
        List<StudentDiscount> rows = studentDiscountRepository.findActiveForGroup(school.getId(), academicYear.getId(), groupId);
        Map<Long, List<String>> discountsByStudent = new HashMap<>();
        Set<Long> siblingHolderIds = new HashSet<>();
        for(StudentDiscount sd : rows){
            Long stuId = sd.getAcademicStudent().getId();
            String discountName = sd.getDiscounthead().getDiscountName();
            discountsByStudent.computeIfAbsent(stuId, k -> new ArrayList<>()).add(discountName);
            if(SIBLING_DISCOUNT_NAME.equalsIgnoreCase(discountName)){
                siblingHolderIds.add(stuId);
            }
        }
        Map<String, Object> overview = new HashMap<>();
        overview.put("allowed", siblingDiscountConfigService.getAllowedPerGroup(school.getId()));
        overview.put("assigned", siblingHolderIds.size());
        overview.put("siblingHolderIds", siblingHolderIds);
        overview.put("discountsByStudent", discountsByStudent);
        return overview;
    }

    /**
     * Name of an Active discount currently held by the student for the school + year, if any.
     * Inactive rows are ignored. Shared by the validate-student endpoint and the save-time check.
     */
    @Transactional(readOnly = true)
    public Optional<String> findActiveDiscountName(Long schoolId, Long academicYearId, Long academicStudentId){
        return studentDiscountRepository
                .findAllBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(schoolId, academicYearId, academicStudentId)
                .stream()
                .filter(sd -> "Active".equalsIgnoreCase(sd.getStatus()))
                .map(sd -> sd.getDiscounthead().getDiscountName())
                .findFirst();
    }

    /** The group must exist for the logged-in school + academic year and be Active. */
    private boolean isGroupUsable(SiblingGroup group, School school, AcademicYear academicYear){
        return group.getSchool() != null && group.getSchool().getId().equals(school.getId())
                && group.getAcademicYear() != null && group.getAcademicYear().getId().equals(academicYear.getId())
                && "Active".equalsIgnoreCase(group.getStatus());
    }

    /**
     * Server-side eligibility checks for assigning the Sibling Discount to one student of a group.
     * Returns null when the student may receive it, otherwise a short, user-facing reason.
     *
     * Checks (in order): student belongs to this school, is a member of the selected group, does not
     * already hold a discount, and the group has not used up the number of sibling discounts allowed
     * by the school's setting (App Config > Discount; defaults to 1 when not configured).
     */
    private String validateSiblingDiscountEligibility(AcademicStudent student, SiblingGroup group, School school, AcademicYear academicYear){
        String name = student.getStudent().getStudentName();

        if(student.getSchool() == null || !student.getSchool().getId().equals(school.getId())){
            return "Student: " + name + " does not belong to this school.";
        }

        List<SiblingGroupStudent> members = siblingGroupStudentRepository.findAllBySiblingGroup(group);
        boolean isMember = members.stream().anyMatch(m -> m.getAcademicStudent().getId().equals(student.getId()));
        if(!isMember){
            return "Student: " + name + " is not a member of the selected sibling group.";
        }

        // Only an Active discount blocks; Inactive rows (e.g. left by a dissolved group) do not.
        Optional<String> activeDiscount = findActiveDiscountName(school.getId(), academicYear.getId(), student.getId());
        if(activeDiscount.isPresent()){
            return "Student: " + name + " is already attached to Discount: " + activeDiscount.get();
        }

        int allowed = siblingDiscountConfigService.getAllowedPerGroup(school.getId());
        List<StudentDiscount> assigned = studentDiscountRepository
                .findActiveByHeadNameForGroup(school.getId(), academicYear.getId(), SIBLING_DISCOUNT_NAME, group.getId());
        if(assigned.size() >= allowed){
            String holders = assigned.stream()
                    .map(sd -> sd.getAcademicStudent().getStudent().getStudentName())
                    .collect(Collectors.joining(", "));
            return "Sibling discount limit reached for this group. Allowed: " + allowed
                    + ", already assigned: " + assigned.size() + " (" + holders + ").";
        }
        return null;
    }
}
