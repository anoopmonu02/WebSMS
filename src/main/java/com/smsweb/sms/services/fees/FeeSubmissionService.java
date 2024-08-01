package com.smsweb.sms.services.fees;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.FullPayment;
import com.smsweb.sms.models.admin.MonthMapping;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.fees.FeeSubmissionBalance;
import com.smsweb.sms.models.fees.FeeSubmissionMonths;
import com.smsweb.sms.models.fees.FeeSubmissionSub;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.universal.Discounthead;
import com.smsweb.sms.models.universal.Feehead;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.MonthMaster;
import com.smsweb.sms.repositories.admin.*;
import com.smsweb.sms.repositories.fees.FeeSubmissionRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.universal.DiscountRepository;
import com.smsweb.sms.repositories.universal.FeeheadRepository;
import com.smsweb.sms.repositories.universal.GradeRepository;
import com.smsweb.sms.repositories.universal.MonthMasterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.ParseException;
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
    private final DiscountclassmapRepository discountclassmapRepository;
    private final FeemonthmapRepository feemonthmapRepository;
    private final DiscountmonthmapRepository discountmonthmapRepository;
    private final AcademicyearRepository academicyearRepository;
    private final SchoolRepository schoolRepository;
    private final MonthMasterRepository monthMasterRepository;
    private final FeeheadRepository feeheadRepository;
    private final DiscountRepository discountRepository;

    @Autowired
    public FeeSubmissionService(FeeSubmissionRepository feeSubmissionRepository, MonthmappingRepository monthmappingRepository, GradeRepository gradeRepository, AcademicStudentRepository academicStudentRepository,
                                FullpaymentRepository fullpaymentRepository, FeemonthmapRepository feemonthmapRepository, FeeclassmapRepository feeclassmapRepository, DiscountclassmapRepository discountclassmapRepository,
                                DiscountmonthmapRepository discountmonthmapRepository, AcademicyearRepository academicyearRepository, SchoolRepository schoolRepository, MonthMasterRepository monthMasterRepository,
                                FeeheadRepository feeheadRepository, DiscountRepository discountRepository){
        this.feeSubmissionRepository = feeSubmissionRepository;
        this.monthmappingRepository = monthmappingRepository;
        this.gradeRepository = gradeRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.fullpaymentRepository = fullpaymentRepository;
        this.feemonthmapRepository = feemonthmapRepository;
        this.feeclassmapRepository = feeclassmapRepository;
        this.discountclassmapRepository = discountclassmapRepository;
        this.discountmonthmapRepository = discountmonthmapRepository;
        this.academicyearRepository = academicyearRepository;
        this.schoolRepository = schoolRepository;
        this.monthMasterRepository = monthMasterRepository;
        this.feeheadRepository = feeheadRepository;
        this.discountRepository = discountRepository;
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
        List<FeeSubmission> feeSubmissionList = feeSubmissionRepository.findTopBySchoolIdAndAcademicYearIdAndAcademicStudentIdOrderByIdDesc(school_id, academic_stu_id);
        return feeSubmissionList!=null && !feeSubmissionList.isEmpty()?feeSubmissionList.get(0):null;
    }


    public Map getPaidMonths(Long school_id, Long academic_id, Long academic_student_id){
        Map paidMonths = new HashMap();
        try{
            List<FeeSubmission> feeSubmissionList = feeSubmissionRepository.findAllBySchoolIdAndAcademicIdAndAcademicStudentId(school_id, academic_id, academic_student_id);
            if(feeSubmissionList!=null && !feeSubmissionList.isEmpty()){
                List<String> monthsList = new ArrayList<>();
                feeSubmissionList.forEach(feeSubmission -> {
                    feeSubmission.getFeeSubmissionMonths().forEach(months ->{
                        monthsList.add(months.getMonthMaster().getMonthName());
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

    public Map getDiscountDetailsBasedOnMonth(Long school_id, Long academic_id, Long academic_stu_id, String monthnames, Long grade_id){
        Map resultMap = new HashMap();
        try{
            List monNames = Arrays.stream(monthnames.split("-")).toList();
            List monIdList = new ArrayList();
            int monthCount = 0;

            for(Object monthNm: monNames){
                MonthMapping monthMapping = monthmappingRepository.findByAcademicYear_IdAndSchool_IdAndMonthMaster_MonthName(academic_id, school_id, monthNm.toString()).orElse(null);
                monIdList.add(monthMapping.getMonthMaster().getId());
                monthCount++;
            }
            /*Grade grade = gradeRepository.findById(grade_id).orElse(null);
            AcademicStudent academicStudent = academicStudentRepository.findById(academic_stu_id).orElse(null);*/
            //Discount Calculated
            List<Object[]> discountData = discountclassmapRepository.findAmountAndDiscountHeadNames(academic_id, school_id, monIdList, grade_id);
            if(discountData!=null && !discountData.isEmpty()){
                List<Map<String, Object>> resultList = new ArrayList<>();
                for (Object[] result : discountData) {
                    try {
                        String discountHeadName = (String) result[1];
                        Map<String, Object> map = new HashMap<>();
                        map.put("amount", (BigDecimal) result[0]);
                        map.put("discountHeadName", discountHeadName);
                        map.put("quantity", Integer.parseInt(result[2].toString()));
                        map.put("discountid", ((Number) result[3]).longValue());
                        map.put("amt", (BigDecimal) result[4]);
                        resultList.add(map);
                    } catch (ClassCastException | NullPointerException | NumberFormatException e) {
                        System.err.println("Error processing fee data record: " + e.getMessage());
                        // Handle the exception according to your application's needs
                        resultMap.put("DiscountError", e.getLocalizedMessage());
                    }
                }
                resultMap.put("discountdata", resultList);
            }
        }catch(Exception e){
            e.printStackTrace();
            resultMap.put("MonthError", e.getLocalizedMessage());
        }
        return resultMap;
    }

    @Transactional
    public Map save(Map<String, String[]> paramsMap){
        Map resultMap = new HashMap();
        try{
            if(paramsMap!=null && !paramsMap.isEmpty()) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyyyyhhmmss");
                List<String> feeSubmissionModelColumns = Arrays.asList("feesubmissiondate", "academicStudent.id", "fullPaymentAmount", "fineAmount", "fineRemark", "discountAmount", "discountHead", "totalAmount",
                        "paidAmount", "balanceAmount", "feeRemark", "headName", "months");
                AcademicStudent student;// = academicStudentRepository.findById(Long.parseLong("0")).orElse(null);
                AcademicYear academicYear = academicyearRepository.findById(14L).orElse(null);
                School school = schoolRepository.findById(4L).orElse(null);
                //Saving fee submission object
                Map<String, Map> feeDataMap = getColumnsValue(paramsMap, feeSubmissionModelColumns);
                if(feeDataMap!=null){
                    boolean proceedFlag = false;
                    Map feeMap = feeDataMap.get("FeeSubmission");
                    Date submissionDate = (Date)feeMap.get("feesubmissiondate");
                    if (feeSubmissionRepository.canSubmitFee(submissionDate)) {
                        proceedFlag = true;
                    } else {
                        proceedFlag = false;
                    }
                    if(proceedFlag){
                        FeeSubmission feeSubmission = new FeeSubmission();
                        if(feeMap!=null){
                            if(feeMap.containsKey("academicStudent.id")){
                                student = (AcademicStudent)feeMap.get("academicStudent.id");
                                feeSubmission.setAcademicStudent(student);
                                resultMap.put("student", student);
                            }
                            feeSubmission.setAcademicYear(academicYear);
                            feeSubmission.setBalanceAmount(feeMap.containsKey("balanceAmount")?new BigDecimal(feeMap.get("balanceAmount").toString()):BigDecimal.ZERO);
                            feeSubmission.setDiscountAmount(feeMap.containsKey("discountAmount")?new BigDecimal(feeMap.get("discountAmount").toString()):BigDecimal.ZERO);
                            feeSubmission.setFineAmount(feeMap.containsKey("fineAmount")?new BigDecimal(feeMap.get("fineAmount").toString()):BigDecimal.ZERO);
                            feeSubmission.setFullPaymentAmount(feeMap.containsKey("fullPaymentAmount")?new BigDecimal(feeMap.get("fullPaymentAmount").toString()):BigDecimal.ZERO);
                            feeSubmission.setPaidAmount(feeMap.containsKey("paidAmount")?new BigDecimal(feeMap.get("paidAmount").toString()):BigDecimal.ZERO);
                            feeSubmission.setTotalAmount(feeMap.containsKey("totalAmount")?new BigDecimal(feeMap.get("totalAmount").toString()):BigDecimal.ZERO);
                            if(feeMap.containsKey("discountHead")){
                                Object value = feeMap.get("discountHead");
                                if (value instanceof Discounthead) {
                                    feeSubmission.setDiscounthead((Discounthead) value);
                                } else {
                                    feeSubmission.setDiscounthead(null);
                                }
                            } else{
                                feeSubmission.setDiscounthead(null);
                            }
                            feeSubmission.setFeeRemark(feeMap.containsKey("feeRemark")?feeMap.get("feeRemark").toString().trim():null);
                            feeSubmission.setFeeSubmissionDate(feeMap.containsKey("feesubmissiondate")?(Date)feeMap.get("feesubmissiondate"):new Date());
                            feeSubmission.setFineRemark(feeMap.containsKey("fineRemark")?feeMap.get("fineRemark").toString().trim():null);
                            feeSubmission.setFullPaymentRemark("");
                            feeSubmission.setReceiptNo("UA/RCT/"+dateFormat.format(new Date()));
                            feeSubmission.setSchool(school);
                            feeSubmission.setStatus("Active");
                        }
                        List<FeeSubmissionSub> submissionSubList = new ArrayList<>();
                        Map<Feehead, BigDecimal> feeSubMap = feeDataMap.get("FeeSubmission_sub");
                        if(feeSubMap!=null){
                            for (Map.Entry<Feehead, BigDecimal> entry : feeSubMap.entrySet()){
                                FeeSubmissionSub submissionSub = new FeeSubmissionSub();
                                submissionSub.setFeehead(entry.getKey());
                                submissionSub.setAmount(entry.getValue());
                                submissionSub.setStatus("Active");
                                submissionSub.setFeeSubmission(feeSubmission);
                                submissionSubList.add(submissionSub);
                            }
                        }
                        Map<String, MonthMaster> feeMonMap = feeDataMap.get("FeeSubmission_mon");
                        List<FeeSubmissionMonths> submissionMonthsList = new ArrayList<>();
                        if(feeMonMap!=null){
                            for (Map.Entry<String, MonthMaster> entry : feeMonMap.entrySet()){
                                FeeSubmissionMonths submissionMonths = new FeeSubmissionMonths();
                                submissionMonths.setMonthMaster(entry.getValue());
                                submissionMonths.setStatus("Active");
                                submissionMonths.setFeeSubmission(feeSubmission);
                                submissionMonthsList.add(submissionMonths);
                            }
                        }
                        FeeSubmissionBalance submissionBalance = new FeeSubmissionBalance();
                        submissionBalance.setBalanceAmount(feeSubmission.getBalanceAmount());
                        submissionBalance.setFeeDate(feeSubmission.getFeeSubmissionDate());
                        submissionBalance.setStatus("Active");

                        feeSubmission.setFeeSubmissionBalance(submissionBalance);
                        submissionBalance.setFeeSubmission(feeSubmission);
                        feeSubmission.setFeeSubmissionSub(submissionSubList);
                        feeSubmission.setFeeSubmissionMonths(submissionMonthsList);
                        feeSubmissionRepository.save(feeSubmission);
                        resultMap.put("Feesubmission", feeSubmission);

                    }
                }
            }
        }catch(Exception e){
            e.printStackTrace();
            resultMap.put("student", null);
            resultMap.put("error", e.getLocalizedMessage());
        }
        return resultMap;
    }

    public Map<String, Map> getColumnsValue(Map<String, String[]> paramsMap, List<String> columnsList){
        Map finalMap = new HashMap();
        Map feeMap = new HashMap();
        Map<Feehead, BigDecimal> feeSubMap = new HashMap();
        Map<String, MonthMaster> feeMonMap = new HashMap();
        try{
            SimpleDateFormat sf = new SimpleDateFormat("dd/MMM/yyyy HH:mm:ss");
            for (Map.Entry<String, String[]> entry : paramsMap.entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();

                System.out.println("Key: " + key);
                System.out.println("Values:");
                if(columnsList.contains(key)){
                    if(values.length>0 && (key.equalsIgnoreCase("headName") || key.equalsIgnoreCase("months"))){
                        for (String value : values) {
                            System.out.println(" - " + value);
                            if(key.equalsIgnoreCase("headName")){
                                //feesubmissionsub model
                                feeSubMap.put(feeheadRepository.findById(Long.parseLong(value.split("###")[2])).get(), new BigDecimal(value.split("###")[1]));
                            } else if(key.equalsIgnoreCase("months")){
                                //feesubmissionmonth model
                                feeMonMap.put(value, monthMasterRepository.findByMonthName(value));
                            }
                        }
                    } else{
                        //feesubmission model
                        String value = values[0];
                        if(value!=null && value.trim()!=""){
                            try {
                                switch (key) {
                                    case "feesubmissiondate":
                                        feeMap.put(key, sf.parse(value));
                                        break;
                                    case "fullPaymentAmount":
                                    case "fineAmount":
                                    case "discountAmount":
                                    case "totalAmount":
                                    case "paidAmount":
                                    case "balanceAmount":
                                        feeMap.put(key, new BigDecimal(value));
                                        break;
                                    case "discountHead":
                                        feeMap.put(key, discountRepository.findByDiscountName(value));
                                        break;
                                    case "academicStudent.id":
                                        feeMap.put(key, academicStudentRepository.findById(Long.parseLong(value)).get());
                                        break;
                                    case "fineRemark":
                                    case "feeRemark":
                                        feeMap.put(key, value);
                                        break;
                                    default:
                                        System.out.println("Unknown key: " + key);
                                        break;
                                }
                            } catch (NumberFormatException e) {
                                System.err.println("Invalid number format for key: " + key + " and value: " + value);
                            } catch (ParseException e) {
                                System.err.println("Error parsing date for key: " + key + " and value: " + value);
                            } catch (Exception e) {
                                System.err.println("Error processing key: " + key + " with value: " + value + " - " + e.getMessage());
                            }
                        }
                    }
                }
            }
            finalMap.put("FeeSubmission", feeMap);
            finalMap.put("FeeSubmission_sub", feeSubMap);
            finalMap.put("FeeSubmission_mon", feeMonMap);
        }catch(Exception e){
            finalMap = null;
            throw new RuntimeException("Error: "+e.getLocalizedMessage());
        }
        return finalMap;
    }

}
