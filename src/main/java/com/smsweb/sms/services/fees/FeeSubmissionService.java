package com.smsweb.sms.services.fees;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.FullPayment;
import com.smsweb.sms.models.admin.MonthMapping;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.MonthMaster;
import com.smsweb.sms.repositories.admin.FeeclassmapRepository;
import com.smsweb.sms.repositories.admin.FeemonthmapRepository;
import com.smsweb.sms.repositories.admin.FullpaymentRepository;
import com.smsweb.sms.repositories.admin.MonthmappingRepository;
import com.smsweb.sms.repositories.fees.FeeSubmissionRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.universal.GradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class FeeSubmissionService {
    private final FeeSubmissionRepository feeSubmissionRepository;
    private final MonthmappingRepository monthmappingRepository;
    private final GradeRepository gradeRepository;
    private final AcademicStudentRepository academicStudentRepository;
    private final FullpaymentRepository fullpaymentRepository;
    private final FeeclassmapRepository feeclassmapRepository;
    private final FeemonthmapRepository feemonthmapRepository;

    @Autowired
    public FeeSubmissionService(FeeSubmissionRepository feeSubmissionRepository, MonthmappingRepository monthmappingRepository, GradeRepository gradeRepository, AcademicStudentRepository academicStudentRepository,
                                FullpaymentRepository fullpaymentRepository, FeemonthmapRepository feemonthmapRepository, FeeclassmapRepository feeclassmapRepository){
        this.feeSubmissionRepository = feeSubmissionRepository;
        this.monthmappingRepository = monthmappingRepository;
        this.gradeRepository = gradeRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.fullpaymentRepository = fullpaymentRepository;
        this.feemonthmapRepository = feemonthmapRepository;
        this.feeclassmapRepository = feeclassmapRepository;
    }

    public List<FeeSubmission> getAllFeeSubmissionByAcademicYear(Long school_id, Long academic_id){
        return feeSubmissionRepository.findAllBySchool_IdAndAcademicYear_Id(school_id, academic_id);
    }

    public List<FeeSubmission> getAllFeeSubmissionForAcademicStudent(Long school_id, Long academic_id, Long academic_stu_id){
        return feeSubmissionRepository.findAllBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(school_id, academic_id, academic_stu_id);
    }

    public List<FeeSubmission> getAllFeeSubmissionByAcademicStudent(Long academic_stu_id){
        return feeSubmissionRepository.findAllByAcademicStudent_Id(academic_stu_id);
    }

    public FeeSubmission getLastFeeSubmissionOfStudentForBalance(Long school_id, Long academic_id, Long academic_stu_id){
        return feeSubmissionRepository.findTopBySchoolIdAndAcademicYearIdAndAcademicStudentIdOrderByIdDesc(school_id, academic_id, academic_stu_id).orElse(null);
    }

    public Map getPaidMonths(Long school_id, Long academic_id, Long academic_student_id){
        Map paidMonths = new HashMap();
        try{
            List<FeeSubmission> feeSubmissionList = feeSubmissionRepository.findAllBySchoolIdAndAcademicIdAndAcademicStudentId(school_id, academic_id, academic_student_id);
            if(feeSubmissionList!=null && !feeSubmissionList.isEmpty()){
                List<MonthMaster> monthsList = new ArrayList<>();
                feeSubmissionList.forEach(feeSubmission -> {
                    feeSubmission.getFeeSubmissionMonths().forEach(months ->{
                        monthsList.add(months.getMonthMaster());
                    });
                });
                paidMonths.put("paidMonths", monthsList);
            }
        }catch(Exception e){
            e.printStackTrace();
            paidMonths.put("MonthError", e.getLocalizedMessage());
        }
        return paidMonths;
    }

    public Map getFeeDetailsBasedOnMonth(Long school_id, Long academic_id, Long academic_stu_id, String monthnames, Long grade_id){
        Map resultMap = new HashMap();
        try{
            List monNames = Arrays.stream(monthnames.split("-")).toList();
            List monIdList = new ArrayList();
            int monthCount = 0;
            Map lst = new HashMap();
            //Map feeDetails = new HashMap<>();
            SimpleDateFormat format = new SimpleDateFormat("dd-MMM-yyyy");

            for(Object monthNm: monNames){
                MonthMapping monthMapping = monthmappingRepository.findByAcademicYear_IdAndSchool_IdAndMonthMaster_MonthName(academic_id, school_id, monthNm.toString()).orElse(null);
                monIdList.add(monthMapping.getMonthMaster().getId());
                monthCount++;
            }
            Grade grade = gradeRepository.findById(grade_id).orElse(null);
            AcademicStudent academicStudent = academicStudentRepository.findById(academic_stu_id).orElse(null);
            List<FeeSubmission> stuFeeSubmissionList = feeSubmissionRepository.findAllBySchoolIdAndAcademicIdAndAcademicStudentId(school_id, academic_id, academic_stu_id);

            if(stuFeeSubmissionList!=null && !stuFeeSubmissionList.isEmpty()){
                for(FeeSubmission submission: stuFeeSubmissionList){
                    monthCount += submission.getFeeSubmissionMonths().size();
                }
            }
            //Full Payment Calculated
            if(monthCount == 12){
                lst.put("lastDate", new Date());
                lst.put("amount",0.0);
                FullPayment fullPayment = fullpaymentRepository.findBySchool_IdAndAcademicYear_IdAndGrade_Id(school_id, academic_id, grade_id).orElse(null);
                if(fullPayment!=null){
                    if(new Date().compareTo(fullPayment.getPaymentLastDate())<=0){
                        lst.put("lastDate", fullPayment.getPaymentLastDate());
                        lst.put("amount", fullPayment.getAmount());
                    }
                }
            }
            //Fees Calculated
            List<Object[]> feeData = feeclassmapRepository.findAmountAndFeeHeadNames(academic_id, school_id, monIdList, grade_id);
            Student student = academicStudent.getStudent();
            int stuCounting = academicStudentRepository.countByStudent(student);
            List lst1 = new ArrayList<>();
            lst1 = processFeeData(student, feeData, stuCounting);
            resultMap.put("paymentlist", lst);
            resultMap.put("feelist", lst1);
        }catch(Exception e){
            e.printStackTrace();
            resultMap.put("MonthError", e.getLocalizedMessage());
        }
        return resultMap;
    }

    public Map getFullPaymentMap(){
        Map fullPaymantMap = new HashMap();
        try{

        }catch(Exception e){

        }
        return fullPaymantMap;
    }

    public List<Map<String, Object>> processFeeData(Student student, List<Object[]> feeData, int stuCounting) {
        //int stuCounting = academicStudentRepository.countByStudent(student);
        System.out.println("feeDatafeeDatafeeDatafeeDatafeeData " + feeData);

        List<Map<String, Object>> resultList = new ArrayList<>();
        if (feeData != null && !feeData.isEmpty()) {
            boolean isOldStudent = student.getStudentType().equalsIgnoreCase("old") || stuCounting > 0;

            for (Object[] result : feeData) {
                try {
                    String feeHeadName = (String) result[1];

                    if (feeHeadName != null && !feeHeadName.trim().isEmpty() &&
                            ((isOldStudent && !feeHeadName.equalsIgnoreCase("Admission Fee")) ||
                                    (!isOldStudent && !feeHeadName.equalsIgnoreCase("Annual Fee")))) {

                        Map<String, Object> map = new HashMap<>();
                        map.put("amount", (BigDecimal) result[0]);
                        map.put("feehead", feeHeadName);
                        map.put("quantity", Integer.parseInt(result[2].toString()));
                        map.put("feeid", ((Number) result[3]).longValue());
                        resultList.add(map);
                    }
                } catch (ClassCastException | NullPointerException | NumberFormatException e) {
                    System.err.println("Error processing fee data record: " + e.getMessage());
                    // Handle the exception according to your application's needs
                }
            }
        }

        return resultList;
    }


}
