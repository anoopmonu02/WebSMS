package com.smsweb.sms.services.student;


import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.SiblingGroup;
import com.smsweb.sms.models.student.SiblingGroupStudent;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import com.smsweb.sms.repositories.admin.SchoolRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.SiblingGroupRepository;
import com.smsweb.sms.repositories.student.SiblingGroupStudentRepository;
import com.smsweb.sms.services.users.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SiblingGroupService {
    private final UserService userService;
    private SiblingGroupRepository siblingGroupRepository;
    private final AcademicyearRepository academicyearRepository;
    private final SchoolRepository schoolRepository;
    private final AcademicStudentRepository academicStudentRepository;
    private final SiblingGroupStudentRepository siblingGroupStudentRepository;

    @Autowired
    public SiblingGroupService(SiblingGroupRepository siblingGroupRepository, AcademicyearRepository academicyearRepository, SchoolRepository schoolRepository, AcademicStudentRepository academicStudentRepository, SiblingGroupStudentRepository siblingGroupStudentRepository, UserService userService){
        this.siblingGroupRepository = siblingGroupRepository;
        this.academicyearRepository = academicyearRepository;
        this.schoolRepository = schoolRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.siblingGroupStudentRepository = siblingGroupStudentRepository;
        this.userService = userService;
    }

    public List<SiblingGroup> getAllSiblingGroups(Long school_id, Long academic_year_id){
        return siblingGroupRepository.findAllBySchool_IdAndAcademicYear_IdAndStatus(school_id, academic_year_id, "Active");
    }

    @Transactional
    public Map save(Map<String, String[]> paramsMap){
        Map resultMap = new HashMap();
        try{
            if(paramsMap!=null && !paramsMap.isEmpty()){
                AcademicYear academicYear = academicyearRepository.findById(14L).orElse(null);
                School school = schoolRepository.findById(4L).orElse(null);
                System.out.println(paramsMap);
                List<SiblingGroup> siblingGroupList = new ArrayList<>();
                List<AcademicStudent> academicStudentList = new ArrayList<>();
                String groupName = "";
                for (Map.Entry<String, String[]> entry : paramsMap.entrySet()) {
                    String key = entry.getKey();
                    String[] values = entry.getValue();
                    System.out.println("Key: " + key);
                    System.out.println("Values:"+values);
                    for (String value : values) {
                        System.out.println(" - " + value);
                        if(key.equalsIgnoreCase("groupname")){
                            groupName = value;
                        } else if(key.equalsIgnoreCase("academicstudent")){
                            AcademicStudent academicStudent = academicStudentRepository.findById(Long.parseLong(value)).get();
                            academicStudentList.add(academicStudent);
                        }
                    }
                }
                int counter = 0;
                //Get student detail if already saved in any group
                String proceedMsg = validateExistingGroupStudent(academicStudentList);
                if(proceedMsg != null && proceedMsg.trim() != ""){
                    if(proceedMsg.startsWith("Found")){
                        resultMap.put("STUDENT_EXIST","Operation not allowed, Student already assigned to a group");
                        return resultMap;
                    }
                }
                if(academicStudentList!=null && !academicStudentList.isEmpty()){
                    SiblingGroup siblingGroup = new SiblingGroup();
                    siblingGroup.setGroupName(groupName);
                    siblingGroup.setAcademicYear(academicYear);
                    siblingGroup.setSchool(school);
                    siblingGroup.setDescription("Saving group with student(s)");
                    siblingGroup.setCreatedBy(userService.getLoggedInUser());
                    SiblingGroup group = siblingGroupRepository.save(siblingGroup);
                    siblingGroupList.add(group);
                    for(AcademicStudent student: academicStudentList){
                        SiblingGroupStudent groupStudent = new SiblingGroupStudent();
                        groupStudent.setAcademicStudent(student);
                        groupStudent.setSiblingGroup(group);
                        siblingGroupStudentRepository.save(groupStudent);
                        counter++;
                    }
                }
                if(academicStudentList.size()==counter){
                    resultMap.put("SAVED","1");
                } else{
                    resultMap.put("NOT_SAVED","1");
                }

                resultMap.put("siblingGroup", siblingGroupList);
            }
        }catch(Exception e){
            e.printStackTrace();
            resultMap.put("siblingGroup", null);
            resultMap.put("error", e.getLocalizedMessage());
        }
        return resultMap;
    }

    public String validateExistingGroupStudent(List<AcademicStudent> academicStudents) {
        String msg = "";
        if (academicStudents == null || academicStudents.isEmpty()) {
            return "Error:No academic student found in list.";
        }

        try {
            List<SiblingGroup> siblingGroups = siblingGroupRepository.findAllBySchool_IdAndAcademicYear_IdAndStatus(4L, 14L, "Active");

            if (siblingGroups == null || siblingGroups.isEmpty()) {
                return "Error:No Sibling-group found.";
            }

            // Collect all AcademicStudents from all sibling groups in one pass
            Set<AcademicStudent> groupStudentSet = siblingGroups.stream()
                    .flatMap(group -> siblingGroupStudentRepository.findAllBySiblingGroup(group).stream())
                    .map(SiblingGroupStudent::getAcademicStudent)
                    .collect(Collectors.toSet());

            // Check if any of the provided academicStudents exist in the group
            boolean isExist = academicStudents.stream().anyMatch(groupStudentSet::contains);

            if (isExist) {
                msg = "Found:Student already assigned to a group.";
            }

        } catch (Exception e) {
            e.printStackTrace();
            msg = "Error:"+e.getLocalizedMessage();
        }
        return msg;
    }


    public Optional<SiblingGroup> getSiblingGroupDetail(Long id){
        return siblingGroupRepository.findById(id);
    }

    @Transactional
    public String deleteSiblingGroup(Long id){
        String msg = "";
        try{
            SiblingGroup siblingGroup = siblingGroupRepository.findById(id).orElse(null);
            siblingGroupRepository.delete(siblingGroup);

            //siblingGroupRepository.deleteById(id);
            msg = "success";
        }catch(Exception e){
            e.printStackTrace();
            msg = "Error: "+e.getLocalizedMessage();
        }
        return msg;
    }

    public List<SiblingGroupStudent> getAllStudentByGroup(Long siblingId){
        System.out.println("Sibling ID: "+siblingId);
        System.out.println("Sibling ID: "+siblingId.getClass());
        try{
            SiblingGroup siblingGroup = siblingGroupRepository.findById(siblingId).orElse(null);
            System.out.println("siblingGroup "+siblingGroup);
            if(siblingGroup!=null){
                List<SiblingGroupStudent> lst = siblingGroup.getSiblingGroupStudents();
                return lst;
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        return null;
    }
}
