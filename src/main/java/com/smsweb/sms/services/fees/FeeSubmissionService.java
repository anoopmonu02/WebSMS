package com.smsweb.sms.services.fees;

import com.smsweb.sms.models.admin.*;
import com.smsweb.sms.models.fees.*;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.student.StudentDiscount;
import com.smsweb.sms.models.universal.Discounthead;
import com.smsweb.sms.models.universal.Feehead;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.MonthMaster;
import com.smsweb.sms.repositories.admin.*;
import com.smsweb.sms.repositories.fees.FeeSubmissionRepository;
import com.smsweb.sms.repositories.fees.ReceiptSequenceRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.StudentDiscountRepository;
import com.smsweb.sms.repositories.universal.DiscountRepository;
import com.smsweb.sms.repositories.universal.FeeheadRepository;
import com.smsweb.sms.repositories.universal.GradeRepository;
import com.smsweb.sms.repositories.universal.MonthMasterRepository;
import com.smsweb.sms.services.users.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

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
    private final ReceiptSequenceRepository receiptSequenceRepository;
    private final FeedateRepository feedateRepository;
    private final FineRepository fineRepository;
    private final StudentDiscountRepository studentDiscountRepository;
    private final UserService userService;

    @Autowired
    public FeeSubmissionService(FeeSubmissionRepository feeSubmissionRepository, MonthmappingRepository monthmappingRepository, GradeRepository gradeRepository, AcademicStudentRepository academicStudentRepository,
                                FullpaymentRepository fullpaymentRepository, FeemonthmapRepository feemonthmapRepository, FeeclassmapRepository feeclassmapRepository, DiscountclassmapRepository discountclassmapRepository,
                                DiscountmonthmapRepository discountmonthmapRepository, AcademicyearRepository academicyearRepository, SchoolRepository schoolRepository, MonthMasterRepository monthMasterRepository,
                                FeeheadRepository feeheadRepository, DiscountRepository discountRepository, ReceiptSequenceRepository receiptSequenceRepository, FeedateRepository feedateRepository, FineRepository fineRepository, StudentDiscountRepository studentDiscountRepository, UserService userService){
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
        this.receiptSequenceRepository = receiptSequenceRepository;
        this.feedateRepository = feedateRepository;
        this.fineRepository = fineRepository;
        this.studentDiscountRepository = studentDiscountRepository;
        this.userService = userService;
    }

    public List<FeeSubmission> getAllFeeSubmissionByAcademicYear(Long school_id, Long academic_id){
        return feeSubmissionRepository.findAllBySchool_IdAndAcademicYear_Id(school_id, academic_id);
    }

    public List<FeeSubmission> getAllFeeSubmissionForAcademicStudent(Long school_id, Long academic_id, Long academic_stu_id){
        return feeSubmissionRepository.findAllBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(school_id, academic_id, academic_stu_id);
    }

    public List<FeeSubmission> getAllFeeSubmissionByAcademicStudent(Long academic_stu_id){
        //return feeSubmissionRepository.findAllByAcademicStudent_Id(academic_stu_id);
        return feeSubmissionRepository.findAllByAcademicStudent_IdAndStatus(academic_stu_id,"Active");
    }

    public List<FeeSubmission> getAllActiveFeeSubmissionByAcademicStudent(Long academic_stu_id){
        return feeSubmissionRepository.findAllByAcademicStudent_IdAndStatus(academic_stu_id, "Active");
    }
    public FeeSubmission getLastFeeSubmissionOfStudentForBalance(Long school_id, Long academic_id, Long academic_stu_id){
        List<FeeSubmission> feeSubmissionList = feeSubmissionRepository.findTopBySchoolIdAndAcademicYearIdAndAcademicStudentIdOrderByIdDesc(school_id, academic_stu_id);
        return feeSubmissionList!=null && !feeSubmissionList.isEmpty()?feeSubmissionList.get(0):null;
    }

    public Optional<FeeSubmission> getFeeSubmissionById(Long id){
        return feeSubmissionRepository.findById(id);
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
            Long discountId = null;
            StudentDiscount studentDiscount = studentDiscountRepository.findBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(school_id, academic_id, academic_stu_id).get();
            if(studentDiscount!=null){
                discountId = studentDiscount.getDiscounthead().getId();
            }

            List<Object[]> discountData = discountclassmapRepository.findAmountAndDiscountHeadNames(academic_id, school_id, monIdList, grade_id, discountId);
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
    public String generateReceiptNumber(String branchCode) {
        int currentYear = Year.now().getValue(); // Get the current year

        // Fetch the sequence for the branch and current year
        ReceiptSequence sequence = receiptSequenceRepository
                .findByBranchCodeAndYear(branchCode, currentYear)
                .orElse(new ReceiptSequence(branchCode, 0, currentYear));

        // Increment the sequence value
        int nextSequence = sequence.getCurrentValue() + 1;

        // Update the currentValue in the database
        sequence.setCurrentValue(nextSequence);
        receiptSequenceRepository.save(sequence);

        // Generate and return the receipt number
        //int padding = (nextSequence < 10000) ? 4 : (nextSequence < 100000) ? 5 : 6;

        //String paddedSequence = String.format("%0" + padding + "d", nextSequence);
        //return String.format("%s/%04d/%d", branchCode, nextSequence, currentYear);
        return String.format("%s/%d/%d", branchCode, currentYear, nextSequence);
        //return String.format("%s-%d-%s", branchCode, currentYear, paddedSequence);
    }

    private String getCodeValue(String schoolName) {
        if (schoolName == null || schoolName.isEmpty()) {
            throw new IllegalArgumentException("School name cannot be null or empty");
        }

        String lowerCaseName = schoolName.toLowerCase();

        if (lowerCaseName.contains("college")) {
            return "UC";
        } else if (lowerCaseName.contains("school")) {
            return "US";
        }

        return ""; // Return an empty string or throw an exception if no match is found
    }

    @Transactional
    public Map save(Map<String, String[]> paramsMap, School school, AcademicYear academicYear){
        Map resultMap = new HashMap();
        try{
            if(paramsMap!=null && !paramsMap.isEmpty()) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyyyyhhmmss");
                List<String> feeSubmissionModelColumns = Arrays.asList("feesubmissiondate", "academicStudent.id", "fullPaymentAmount", "fineAmount", "fineRemark", "discountAmount", "discountHead", "totalAmount",
                        "paidAmount", "balanceAmount", "feeRemark", "headName", "months");
                AcademicStudent student;// = academicStudentRepository.findById(Long.parseLong("0")).orElse(null);
                String schoolCodeVal = getCodeValue(school.getSchoolName());
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
                            feeSubmission.setReceiptNo(generateReceiptNumber(schoolCodeVal));
                            //feeSubmission.setReceiptNo("UA/RCT/"+dateFormat.format(new Date()));
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
                        submissionBalance.setStudent(feeSubmission.getAcademicStudent().getStudent());
                        submissionBalance.setStatus("Active");

                        feeSubmission.setFeeSubmissionBalance(submissionBalance);
                        submissionBalance.setFeeSubmission(feeSubmission);
                        feeSubmission.setFeeSubmissionSub(submissionSubList);
                        feeSubmission.setFeeSubmissionMonths(submissionMonthsList);
                        feeSubmission.setCreatedBy(userService.getLoggedInUser().getUsername());
                        feeSubmissionRepository.save(feeSubmission);
                        resultMap.put("Feesubmission", feeSubmission);
                        resultMap.put("feeid", feeSubmission.getId());
                    } else{
                        resultMap.put("fee_submission_not_allowed", "Fee Submission not allowed, Current submission date is less than the last submitted date.");
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

    public Map calculateFeeReminder(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        Map responseMap  = new HashMap();
        try{
            Map<Long, Map> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                Long gradeId = Long.valueOf(paramsMap.get("grade"));
                Long secId = Long.valueOf(paramsMap.get("section"));
                Long mediumId = Long.valueOf(paramsMap.get("medium"));
                String months = paramsMap.get("checkBoxes");
                String lastDate = paramsMap.get("lastdate");
                if(paramsMap!=null && paramsMap.containsKey("month")){
                    months = paramsMap.get("month");
                }
                System.out.println("Grade: "+gradeId);
                System.out.println("Sec: "+secId);
                System.out.println("Medium: "+mediumId);
                System.out.println("Months: "+months);
                System.out.println("Date: "+lastDate);
                List<MonthMaster> selectedMonthsList = new ArrayList<>();
                List<Long> monIdList = new ArrayList<>();

                for(int i=0;i<months.split("-").length;i++){
                    selectedMonthsList.add(monthMasterRepository.findById(Long.valueOf(months.split("-")[i])).orElse(null));
                    monIdList.add(Long.valueOf(months.split("-")[i]));
                }
                Fine fine = fineRepository.findAllByAcademicYear_IdAndSchool_Id(academicYear.getId(), school.getId()).get(0);
                List<AcademicStudent> academicStudentList = academicStudentRepository.findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatus(school.getId(), mediumId, gradeId, secId, academicYear.getId(), "Active");
                //AcademicYear academicYear = academicyearRepository.findById(14L).orElse(null);
                if(academicStudentList!=null && !academicStudentList.isEmpty()){
                    System.out.println("Academic Students: "+academicStudentList.size());
                    for(AcademicStudent academicStudent: academicStudentList){
                        Map stuMap = new HashMap<>();
                        BigDecimal balanceAmount = BigDecimal.ZERO;
                        int noOfFeeSubmitted = feeSubmissionRepository.countAllByAcademicYear_IdAndSchool_IdAndAcademicStudent_IdAndStatus(academicYear.getId(), school.getId(), academicStudent.getId(), "Active");
                        if(noOfFeeSubmitted>0){
                            //Atleast 1 feesubmission happen for this student
                            //Get all submitted months
                            List<MonthMaster> submittedMonthsList = new ArrayList<>();
                            List<FeeSubmission> feeSubmissions = feeSubmissionRepository.findAllByAcademicStudent_IdAndStatus(academicStudent.getId(),"Active");
                            if(feeSubmissions!=null){
                                for(FeeSubmission submission: feeSubmissions){
                                    //Calculated All submitted months
                                    for(FeeSubmissionMonths submissionMonths : submission.getFeeSubmissionMonths()){
                                        submittedMonthsList.add(submissionMonths.getMonthMaster());
                                    }
                                    balanceAmount = submission.getFeeSubmissionBalance().getBalanceAmount();
                                }
                            }
                            System.out.println("submittedMonthsList: "+submittedMonthsList);
                            if(submittedMonthsList!=null && !submittedMonthsList.isEmpty()){
                                if(submittedMonthsList.containsAll(selectedMonthsList)){
                                    //All selected months fee already submitted
                                    //Check only if any balance amount available
                                    stuMap.put("amount", balanceAmount);
                                    stuMap.put("fineAmount", 0);
                                    stuMap.put("monthsList", "");
                                    stuMap.put("headList", "");
                                    stuMap.put("academicStudent", academicStudent);
                                    finalDataMap.put(academicStudent.getId(), stuMap);
                                } else{
                                    //Fetching months not submitted but selected
                                    List<MonthMaster> restMonthsList = selectedMonthsList.stream()
                                            .filter(monthMaster -> !submittedMonthsList.contains(monthMaster))
                                            .collect(Collectors.toList());
                                    System.out.println("Rest Months: "+restMonthsList);
                                    BigDecimal amt = BigDecimal.ZERO;
                                    BigDecimal fineAmount = BigDecimal.ZERO;
                                    BigDecimal discountAmount = BigDecimal.ZERO;
                                    String headNames  = "";
                                    //Calculate Fee for rest months
                                    List<Object[]> amtHeadList = feeclassmapRepository.findAmountAndFeeHeadNames(academicYear.getId(), school.getId(), restMonthsList.stream().map(MonthMaster::getId).collect(Collectors.toList()),gradeId);
                                    System.out.println("-----");
                                    String feeTypeToexclude = academicStudent.getStudent().getStudentType().equalsIgnoreCase("Old")?"Admission Fee":"Annual Fee";
                                    if(amtHeadList!=null && !amtHeadList.isEmpty()){
                                        for(Object[] rowData : amtHeadList){
                                            if(!feeTypeToexclude.equalsIgnoreCase(rowData[1].toString())){
                                                headNames+=rowData[1]+", ";
                                                System.out.println("rowData[0] "+rowData[0]);
                                                System.out.println("rowData[0] "+rowData[0].getClass());
                                                amt=amt.add((BigDecimal) rowData[0]);
                                            }
                                        }
                                    } else{
                                        responseMap.put("FEE_CLASS_MAP_NOT_FOUND","Fee-class-map not found");
                                    }
                                    System.out.println("headNames "+headNames+" amt:"+amt);
                                    //Calculate Fine
                                    //int monthDiff = monthmappingService.monthDifference(14L, 4L, lastMonthName, subDate);
                                    int monthDiff = monthmappingRepository.findMonthDifference(academicYear.getId(), school.getId(), restMonthsList.get(0).getMonthName(), new SimpleDateFormat("dd/MMM/yyyy").format(new Date()));
                                    List<FeeDate> feeDates = feedateRepository.findByAcademicYearAndSchoolAndGivenMonth(academicYear.getId(), school.getId(), LocalDate.now().getMonthValue());
                                    FeeDate feeDate = null;
                                    if(feeDates!=null && !feeDates.isEmpty()){
                                        feeDate = feeDates.get(0);
                                    }
                                    int cdiff = monthmappingRepository.currentFeeDateDifference(new SimpleDateFormat("dd/MMM/yyyy").format(feeDate.getFeeSubmissiondate()), new SimpleDateFormat("dd/MMM/yyyy").format(new Date()));
                                    try {
                                        if (monthDiff >= 3) {
                                            fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(fine.getMaxCalculated()));
                                        } else if (monthDiff == 2) {
                                            if (cdiff<0) {
                                                fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(2));
                                            } else {
                                                fineAmount = BigDecimal.valueOf(fine.getFineAmount());
                                            }
                                        } else if (monthDiff == 1) {
                                            if (cdiff<0) {
                                                fineAmount = BigDecimal.valueOf(fine.getFineAmount());
                                            } else {
                                                fineAmount = BigDecimal.ZERO;
                                            }
                                        } else {
                                            fineAmount = BigDecimal.ZERO;
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        //responseMap.put("FINE_ERROR", "Error in calculating fine: "+e.getLocalizedMessage());
                                        responseMap.put("error", "Error in calculating fine: "+e.getLocalizedMessage());
                                    }
                                    //Calculate Discount
                                    //BigDecimal discountAmt = BigDecimal.ZERO;
                                    StudentDiscount studentDiscount = studentDiscountRepository.findBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(school.getId(), academicYear.getId(), academicStudent.getId()).orElse(null);
                                    if(studentDiscount!=null){
                                        List<Object[]> disAmtHeadList = discountclassmapRepository.findAmountAndDiscountHeadNames(academicYear.getId(), school.getId(), restMonthsList.stream().map(MonthMaster::getId).collect(Collectors.toList()),gradeId, studentDiscount.getDiscounthead().getId());
                                        if(disAmtHeadList!=null && !disAmtHeadList.isEmpty()){
                                            for(Object[] rowData : disAmtHeadList){
                                                if(studentDiscount.getDiscounthead().getDiscountName().equalsIgnoreCase(rowData[1].toString())){
                                                    System.out.println("rowData[0] "+rowData[0]);
                                                    System.out.println("rowData[0] "+rowData[0].getClass());
                                                    discountAmount=discountAmount.add((BigDecimal) rowData[0]);
                                                }
                                            }
                                        } else{
                                            //responseMap.put("DISCOUNT_CLASS_MAP_NOT_FOUND","Discount-class-map not found");
                                            responseMap.put("error","Discount-class-map not found");
                                        }
                                    } else{
                                        System.out.println("Discount not assigned to student: "+academicStudent.getStudent().getStudentName());
                                    }
                                    String montnNames = "";
                                    for(MonthMaster monthMaster : restMonthsList){
                                        montnNames+=monthMaster.getMonthName()+", ";
                                    }
                                    BigDecimal finalamt = balanceAmount.add(amt).add(fineAmount);
                                    finalamt = finalamt.subtract(discountAmount);
                                    stuMap.put("amount", finalamt);
                                    stuMap.put("fineAmount", fineAmount);
                                    stuMap.put("monthsList", montnNames);
                                    stuMap.put("headList", headNames);
                                    stuMap.put("academicStudent", academicStudent);
                                    finalDataMap.put(academicStudent.getId(), stuMap);
                                }
                            }
                        } else{
                            //No fee submission happen till
                            System.out.println("No Fee submitted "+academicStudent.getStudent().getStudentName());
                            BigDecimal amt = BigDecimal.ZERO;
                            BigDecimal fineAmount = BigDecimal.ZERO;
                            BigDecimal discountAmount = BigDecimal.ZERO;
                            String headNames  = "";
                            List<MonthMapping> mmList = monthmappingRepository.findMonthsByPriority(academicYear.getId(), school.getId(), monIdList);
                            if(mmList!=null && !mmList.isEmpty()){
                                List<MonthMaster> allMonthsList = mmList.stream()
                                        .map(MonthMapping::getMonthMaster)
                                        .collect(Collectors.toList());
                                if(allMonthsList!=null && !allMonthsList.isEmpty()){
                                    String feeTypeToexclude = academicStudent.getStudent().getStudentType().equalsIgnoreCase("Old")?"Admission Fee":"Annual Fee";
                                    List<Object[]> feedetails = feeclassmapRepository.findAmountAndFeeHeadNames(academicYear.getId(), school.getId(), allMonthsList.stream().map(MonthMaster::getId).collect(Collectors.toList()), gradeId);
                                    if(feedetails!=null && !feedetails.isEmpty()){
                                        for(Object[] rowData : feedetails){
                                            if(!feeTypeToexclude.equalsIgnoreCase(rowData[1].toString())){
                                                headNames+=rowData[1]+", ";
                                                amt=amt.add((BigDecimal) rowData[0]);
                                            }
                                        }
                                    } else{
                                        System.out.println("No fee class mapping found for class: "+gradeId);
                                        //responseMap.put("FEE_CLASS_MAP_NOT_FOUND","Fee-class-map not found");
                                        responseMap.put("error","Fee-class-map not found");
                                    }
                                    //int monthDiff = monthmappingRepository.findMonthDifference(14L, 4L, allMonthsList.get(0).getMonthName(), new SimpleDateFormat("dd/MMM/yyyy").format(new Date()));
                                    List<FeeDate> feeDates = feedateRepository.findByAcademicYearAndSchoolAndGivenMonth(academicYear.getId(), school.getId(), LocalDate.now().getMonthValue());
                                    FeeDate feeDate = null;
                                    if(feeDates!=null && !feeDates.isEmpty()){
                                        feeDate = feeDates.get(0);
                                    }
                                    if(feeDate!=null){
                                        int cdiff = monthmappingRepository.currentFeeDateDifference(new SimpleDateFormat("dd/MMM/yyyy").format(feeDate.getFeeSubmissiondate()), new SimpleDateFormat("dd/MMM/yyyy").format(new Date()));
                                        int monthdiff = monthmappingRepository.firstMonthDifference(new SimpleDateFormat("dd/MMM/yyyy").format(new Date()), academicYear.getStartDate());
                                        try {
                                            if (monthdiff > 2) {
                                                fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(fine.getMaxCalculated()));
                                            } else if (monthdiff<0 && monthdiff >= -2) {
                                                if (cdiff<0) {
                                                    fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(monthdiff + (monthdiff * monthdiff) + 1));
                                                } else {
                                                    fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(monthdiff + (monthdiff * monthdiff)));//(monthdiff + (monthdiff * monthdiff)) * fine.getFineAmount();
                                                }
                                            } else if (monthdiff < -2) {
                                                fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(fine.getMaxCalculated()));
                                            } else {
                                                if (cdiff<0) {
                                                    fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(monthdiff + 1));
                                                } else {
                                                    fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(monthdiff));
                                                }
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            //responseMap.put("FINE_ERROR", "Error in calculating fine: "+e.getLocalizedMessage());
                                            responseMap.put("error", "Error in calculating fine: "+e.getLocalizedMessage());
                                        }
                                    } else{
                                        responseMap.put("error","Fee date not found, Please add fee date first");
                                    }

                                    //Calculate Discount
                                    BigDecimal discountAmt = BigDecimal.ZERO;
                                    StudentDiscount studentDiscount = studentDiscountRepository.findBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(school.getId(), academicYear.getId(), academicStudent.getId()).orElse(null);
                                    if(studentDiscount!=null){
                                        List<Object[]> disAmtHeadList = discountclassmapRepository.findAmountAndDiscountHeadNames(academicYear.getId(), school.getId(), allMonthsList.stream().map(MonthMaster::getId).collect(Collectors.toList()),gradeId, studentDiscount.getDiscounthead().getId());
                                        if(disAmtHeadList!=null && !disAmtHeadList.isEmpty()){
                                            for(Object[] rowData : disAmtHeadList){
                                                if(studentDiscount.getDiscounthead().getDiscountName().equalsIgnoreCase(rowData[1].toString())){
                                                    System.out.println("rowData[0] "+rowData[0]);
                                                    System.out.println("rowData[0] "+rowData[0].getClass());
                                                    discountAmt=discountAmt.add((BigDecimal) rowData[0]);
                                                }
                                            }
                                        } else{
                                            responseMap.put("DISCOUNT_CLASS_MAP_NOT_FOUND","Discount-class-map not found");
                                        }
                                    } else{
                                        System.out.println("Discount not assigned to student: "+academicStudent.getStudent().getStudentName());
                                    }
                                    String montnNames = "";
                                    for(MonthMaster monthMaster : allMonthsList){
                                        montnNames+=monthMaster.getMonthName()+", ";
                                    }
                                    BigDecimal finalamt = balanceAmount.add(amt).add(fineAmount);
                                    finalamt = finalamt.subtract(discountAmount);
                                    stuMap.put("amount", finalamt);
                                    stuMap.put("fineAmount", fineAmount);
                                    stuMap.put("monthsList", montnNames);
                                    stuMap.put("headList", headNames);
                                    stuMap.put("academicStudent", academicStudent);
                                    finalDataMap.put(academicStudent.getId(), stuMap);
                                } else{
                                    //write logs
                                }
                            } else{
                                //Write logs
                            }
                        }
                    }
                } else{
                    responseMap.put("STUDENT_NOT_FOUND","Student Not found!");
                }
            }
            //System.out.println("finalData: "+finalDataMap);
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    @Transactional
    public int calculateFine(List<String> selectedMonths, School school, AcademicYear academicYear, int maxFineAmount, Fine fine){
        int finalFineAmount = 0;
        try{
            for(String mnName : selectedMonths){
                int monDiff = feeSubmissionRepository.getMonthDiffForFine(mnName, academicYear.getId(), school.getId());
                if(monDiff>0){
                    finalFineAmount = 0;
                } else if(monDiff==0){
                    FeeDate feedate = feedateRepository.findByAcademicYear_IdAndSchool_IdAndMonthMaster_MonthName(academicYear.getId(), school.getId(), mnName).orElse(null);
                    if(feedate!=null){
                        String formattedDate = new SimpleDateFormat("dd/MMM/yyyy").format(feedate.getFeeSubmissiondate());
                        int dateDifference = feeSubmissionRepository.getDateDifference(formattedDate);
                        if(dateDifference<0){
                            finalFineAmount+=fine.getFineAmount();
                        }
                    } else{
                        throw new RuntimeException("No Fee date found for month: "+mnName);
                    }
                } else{
                    finalFineAmount+=fine.getFineAmount();
                }
            }
            if(finalFineAmount>maxFineAmount){
                finalFineAmount = maxFineAmount;
            }
        }catch(Exception e){
            e.printStackTrace();
            finalFineAmount = -1;
            throw new RuntimeException("Error in calculating fine",e);
        }
        return finalFineAmount;
    }

    public Map<String, Object> getFeeReceiptData(Long id, School school, AcademicYear academicYear) {
        Map<String, Object> modelData = new HashMap<>();
        try {
            SimpleDateFormat sf = new SimpleDateFormat("dd-MMM-yyyy");
            FeeSubmission feeSubmission = getFeeSubmissionById(id).orElse(null);

            if (feeSubmission == null) {
                modelData.put("error", "Fee submission not found for ID: " + id);
                return modelData;
            }

            AcademicStudent academicStudent = feeSubmission.getAcademicStudent();
            if (academicStudent == null) {
                modelData.put("studentError", "Academic Student not found!");
                return modelData;
            }

            modelData.put("student", academicStudent);
            modelData.put("school", academicStudent.getSchool());
            modelData.put("academicYear", academicStudent.getAcademicYear().getSessionFormat());
            modelData.put("hasStudent", true);

            List<String> slipDateList = new ArrayList<>();
            if (feeSubmission != null) {
                modelData.put("feeSubmission", feeSubmission);
                modelData.put("hasFeeSubmission", true);

                HashMap<MonthMaster, Date> submittedMonthMap = new LinkedHashMap<>();
                List<MonthMapping> monthMappingList = monthmappingRepository.findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(academicYear.getId(), school.getId());
                List<FeeSubmission> feeSubmissionList = getAllActiveFeeSubmissionByAcademicStudent(academicStudent.getId());

                if (feeSubmissionList != null) {
                    for (FeeSubmission submission : feeSubmissionList) {
                        for (FeeSubmissionMonths feeMonths : submission.getFeeSubmissionMonths()) {
                            submittedMonthMap.put(feeMonths.getMonthMaster(), submission.getFeeSubmissionDate());
                        }
                    }
                }

                int i = 1;
                for (MonthMapping mm : monthMappingList) {
                    String dateString = "Month-" + i + " ####(" + mm.getMonthMaster().getMonthName().toUpperCase() + "): ####";
                    if (submittedMonthMap.containsKey(mm.getMonthMaster())) {
                        dateString += "PAID " + sf.format(submittedMonthMap.get(mm.getMonthMaster()));
                    }
                    slipDateList.add(dateString);
                    i++;
                }
                modelData.put("feeSubmittedMonths", slipDateList);
                modelData.put("feesublist", feeSubmission.getFeeSubmissionSub());
            } else {
                modelData.put("feeSubmissionError", "Fee not found for: " + academicStudent.getStudent().getStudentName() + "!");
            }
        } catch (Exception e) {
            modelData.put("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
        System.out.println("modelData>>>>>>>> "+modelData);
        return modelData;
    }

    public FeeSubmission getFeeDetailsForReceipt(String receipt_no, School school, AcademicYear academicYear){
        try{
            String finalReceiptNo = receipt_no.trim().replace("-","/");
            //Optional<FeeSubmission> feesubmission = feeSubmissionRepository.findByReceiptNoAndStatusAndSchool_IdAndAcademicYear_Id(receipt_no, "Active", school.getId(), academicYear.getId());
            FeeSubmission feesubmission = feeSubmissionRepository.findByReceiptNoIgnoreCaseAndStatusAndSchoolIdAndAcademicYearId(finalReceiptNo, "Active", school.getId(), academicYear.getId());
            System.out.println("submission: "+feesubmission);
            return feesubmission!=null? feesubmission : null;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public Map calculateFeeSubmissionUserWise(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        Map responseMap  = new HashMap();
        try{
            Map<String, Object> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                if(paramsMap.containsKey("selectedOption")){
                    if(paramsMap.get("selectedOption").equalsIgnoreCase("today")){
                        String currentDate = paramsMap.get("todayDate");
                        System.out.println("currentDate:"+currentDate);
                        List<Object[]> userWiseFeeCollection = feeSubmissionRepository.findFeeSubmissionAggregatesForCurrentDate(currentDate, school.getId(), academicYear.getId());
                        finalDataMap.put("userWiseFeeCollection", (CollectionUtils.isEmpty(userWiseFeeCollection))? "No Data found": userWiseFeeCollection);
                        List<FeeSubmission> todayFeeCollectionDetails = feeSubmissionRepository.findAllFeeDetailsByUser("Active", school.getId(), academicYear.getId(), currentDate, null, null);
                        finalDataMap.put("todayFeeCollectionDetails", (CollectionUtils.isEmpty(todayFeeCollectionDetails))? "No Fee details found for current date:" + currentDate: todayFeeCollectionDetails);
                    } else if(paramsMap.get("selectedOption").equalsIgnoreCase("range")){
                        String startDate = paramsMap.get("startDate");
                        String endDate = paramsMap.get("endDate");
                        System.out.println("start-end:"+startDate+"-"+endDate);
                        List<Object[]> userWiseFeeCollection = feeSubmissionRepository.findFeeSubmissionAggregatesForDateRange(startDate, endDate, school.getId(), academicYear.getId());
                        finalDataMap.put("userWiseFeeCollection", (CollectionUtils.isEmpty(userWiseFeeCollection))? "No Data found": userWiseFeeCollection);
                        List<FeeSubmission> dateRangeFeeCollectionDetails = feeSubmissionRepository.findAllFeeDetailsByUser("Active", school.getId(), academicYear.getId(),  null, startDate, endDate);
                        finalDataMap.put("dateRangeFeeCollectionDetails", (CollectionUtils.isEmpty(dateRangeFeeCollectionDetails))? "No Fee details found for dates:" + startDate + " and " + endDate: dateRangeFeeCollectionDetails);
                    }
                }
            }
            //System.out.println("finalData: "+finalDataMap);
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    public Map calculateCancelledFees(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        Map responseMap  = new HashMap();
        try{
            Map<String, Object> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                String startDate = paramsMap.get("startDate");
                String endDate = paramsMap.get("endDate");
                List<FeeSubmission> dateRangeFeeCollectionDetails = feeSubmissionRepository.findAllFeeDetailsBasedOnStatusAndInDateRange("Inactive", school.getId(), academicYear.getId(),  null, startDate, endDate);
                finalDataMap.put("dateRangeFeeCollectionDetails", (CollectionUtils.isEmpty(dateRangeFeeCollectionDetails))? "No Fee details found for dates between:" + startDate + " and " + endDate: dateRangeFeeCollectionDetails);
            }
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    public Map calculateTotalSubmittedFees(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        Map responseMap  = new HashMap();
        try{
            Map<String, Object> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                System.out.println("paramsMap:: "+paramsMap);
                String medium = paramsMap.get("medium");

                List<FeeSubmission> totalFeeCollectionDetails = feeSubmissionRepository.findAllFeeSubmittedDetails(school.getId(), academicYear.getId(), Long.parseLong(medium));
                finalDataMap.put("totalFeeCollectionDetails", (CollectionUtils.isEmpty(totalFeeCollectionDetails))? "No Fee details found for medium": totalFeeCollectionDetails);
            }
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    public Map calculateTotalSubmittedFeesGradeWise(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        Map responseMap  = new HashMap();
        try{
            Map<String, Object> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                System.out.println("paramsMap:: "+paramsMap);
                String medium = paramsMap.get("medium");
                String section = paramsMap.get("section");
                String grade = paramsMap.get("grade");

                List<FeeSubmission> totalFeeCollectionDetails = feeSubmissionRepository.findAllFeeSubmittedDetailsGradeWise(school.getId(), academicYear.getId(), Long.parseLong(medium), Long.parseLong(grade), Long.parseLong(section));
                finalDataMap.put("totalFeeCollectionDetails", (CollectionUtils.isEmpty(totalFeeCollectionDetails))? "No Fee details found for selected Grade-Section": totalFeeCollectionDetails);
            }
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    public Map getSubmittedFeeDetailForGrade(School school, AcademicYear academicYear, Map<String, String> paramsMap){
        Map responseMap  = new HashMap();
        try{
            if(paramsMap!=null && !paramsMap.isEmpty()){
                String medium = paramsMap.get("medium");
                String section = paramsMap.get("section");
                String grade = paramsMap.get("grade");
                SimpleDateFormat sf = new SimpleDateFormat("dd/MMM/yyyy");
                List<MonthMapping> mmList = monthmappingRepository.findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(academicYear.getId(), school.getId());
                List<String> monthNamesList = mmList.stream()
                        .map(mm -> mm.getMonthMaster().getMonthName())
                        .collect(Collectors.toList());
                responseMap.put("MONTHS",monthNamesList);
                List<Long> orderedMonthIds = mmList.stream()
                        .map(mm -> mm.getMonthMaster().getId())
                        .collect(Collectors.toList());
                List<AcademicStudent> academicStudents = academicStudentRepository.findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatus(school.getId(), Long.parseLong(medium), Long.parseLong(grade), Long.parseLong(section), academicYear.getId(), "Active");
                if(academicStudents!=null && !academicStudents.isEmpty()){
                    Map stuFeeSubMap = new HashMap();
                    for(AcademicStudent student:academicStudents){
                        Map stuFeeDataMap = new HashMap();
                        List<FeeSubmission> feeSubmissions = feeSubmissionRepository.findAllByAcademicStudent_IdAndStatus(student.getId(), "Active");
                        List depositedFeeList = new ArrayList();
                        int monthCount = 0;
                        if(feeSubmissions!=null && !feeSubmissions.isEmpty()){
                            //Calculate Fee + discount + Fine
                            BigDecimal fineAmount = BigDecimal.ZERO;
                            for(FeeSubmission feeSubmission:feeSubmissions){
                                List<FeeSubmissionMonths> feeSubmissionMonths = feeSubmission.getFeeSubmissionMonths();
                                /**
                                 * divide submitted fees by submitted months when every month fee calculated by fee mapped to class
                                 * calculate any discount, fine if applicable
                                 * */
                                BigDecimal paidAmount = feeSubmission.getPaidAmount();
                                BigDecimal discountAmt = feeSubmission.getDiscountAmount();
                                BigDecimal amountSubmitted = BigDecimal.valueOf(0.0);
                                BigDecimal discountReceived = BigDecimal.valueOf(0.0);
                                fineAmount = feeSubmission.getFineAmount();
                                amountSubmitted = paidAmount;
                                discountReceived = discountAmt;
                                if(feeSubmissionMonths!=null && feeSubmissionMonths.size()>0){
                                    //Months ids fetched which are submitted based on fee_submission

                                    //Calculating Fees based on month
                                    //List<Long> monthMasterIds = feeSubmissionMonths.stream().map(fsm -> fsm.getMonthMaster().getId()).collect(Collectors.toList());
                                    List<Long> monthMasterIds = feeSubmissionMonths.stream()
                                            .map(fsm -> fsm.getMonthMaster().getId())
                                            .sorted(Comparator.comparingInt(orderedMonthIds::indexOf))
                                            .collect(Collectors.toList());
                                    monthCount+=monthMasterIds.size();
                                    for(Long monthId:monthMasterIds){
                                        List<Long> monthIdList = new ArrayList<>();
                                        monthIdList.add(monthId);
                                        Map feeDetailMap = new HashMap();
                                        feeDetailMap.put("receipt", feeSubmission.getReceiptNo());
                                        feeDetailMap.put("submitId", feeSubmission.getId());
                                        feeDetailMap.put("totalPaidAmount",paidAmount);
                                        feeDetailMap.put("totalDiscountAmount",discountAmt);
                                        feeDetailMap.put("feeSubmitted",BigDecimal.valueOf(0.0));
                                        BigDecimal discountAppliedForMonth = BigDecimal.ZERO;
                                        //Calculating Discount based on month
                                        if (discountAmt.compareTo(BigDecimal.ZERO) > 0 && feeSubmission.getDiscounthead()!=null) {
                                            //System.out.println("Discount is greater than 0");
                                            List<Object[]> discountBasedOnMonths = discountclassmapRepository.findAmountAndDiscountHeadNames(academicYear.getId(), school.getId(), monthIdList, Long.parseLong(grade), feeSubmission.getDiscounthead().getId());
                                            feeDetailMap.put("discountApplied", BigDecimal.valueOf(0.0));
                                            if(discountBasedOnMonths!=null && !discountBasedOnMonths.isEmpty()){
                                                discountAppliedForMonth = (discountBasedOnMonths.get(0)[0]!=null)?new BigDecimal(""+discountBasedOnMonths.get(0)[0]): BigDecimal.valueOf(0.0);
                                                feeDetailMap.put("discountApplied", discountAppliedForMonth);
                                                //discountReceived = discountAmt.subtract(discountAppliedForMonth);
                                            }
                                        }

                                        List<Object[]> feesBasedOnMonths = feeclassmapRepository.findAmountAndFeeHeadNames(academicYear.getId(), school.getId(), monthIdList, Long.parseLong(grade));
                                        BigDecimal amt = BigDecimal.ZERO;
                                        if(feesBasedOnMonths!=null && !feesBasedOnMonths.isEmpty()) {
                                            //fee heads + amount for selected months
                                            for(Object[] obj : feesBasedOnMonths){
                                                BigDecimal amount = (obj[0] != null)
                                                        ? new BigDecimal(obj[0].toString())
                                                        : BigDecimal.ZERO;
                                                amt = amt.add(amount);
                                            }
                                            //BigDecimal feesubmitformonth = (feesBasedOnMonths.get(0)[0] != null) ? new BigDecimal("" + feesBasedOnMonths.get(0)[0]) : BigDecimal.valueOf(0.0);
                                            if (amt.compareTo(amountSubmitted) >= 0) {
                                                feeDetailMap.put("feeSubmitted", amt.subtract(discountAppliedForMonth));
                                            } else {
                                                amountSubmitted = paidAmount.subtract(amt);
                                                feeDetailMap.put("feeSubmitted", amt.subtract(discountAppliedForMonth));
                                            }
                                        }

                                        feeDetailMap.put("submitDate",sf.format(feeSubmission.getFeeSubmissionDate()));
                                        feeDetailMap.put("month_"+monthId.toString(),monthId.toString());
                                        feeDetailMap.put("fineAmount",fineAmount);
                                        feeDetailMap.put("academicStudent", student);
                                        feeDetailMap.put("blankData",0);
                                        fineAmount = BigDecimal.ZERO;
                                        depositedFeeList.add(feeDetailMap);
                                    }
                                }
                            }
                            if(monthCount<12){
                                depositedFeeList = setBlankForNotSubmittedMonth(monthCount, student, depositedFeeList);
                            }
                            //System.out.println("depositedFeeList:"+depositedFeeList);
                            stuFeeSubMap.put(""+student.getId(), depositedFeeList);
                        } else{
                            depositedFeeList = setBlankForNotSubmittedMonth(monthCount, student, depositedFeeList);
                            //System.out.println("depositedFeeList:"+depositedFeeList);
                            stuFeeSubMap.put(""+student.getId(), depositedFeeList);
                        }
                    }
                    responseMap.put("FEE_DATA", stuFeeSubMap);

                } else{
                    responseMap.put("NO_STUDENT_FOUND","No student found for selected grade.");
                }
            } else {
                //No class found
                responseMap.put("NO_PARAMS_FOUND","No selected parameters found.");
            }
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    public List setBlankForNotSubmittedMonth(int monthCount, AcademicStudent student, List depositedFeeList){
        try{
            for(int k=monthCount;k<12;k++){
                Map feeDetailMap = new HashMap();
                feeDetailMap.put("receipt", "");
                feeDetailMap.put("submitId", "");
                feeDetailMap.put("totalPaidAmount", BigDecimal.valueOf(0.0));
                feeDetailMap.put("totalDiscountAmount",BigDecimal.valueOf(0.0));
                feeDetailMap.put("feeSubmitted",BigDecimal.valueOf(0.0));
                feeDetailMap.put("discountApplied", BigDecimal.valueOf(0.0));
                feeDetailMap.put("feeSubmitted", BigDecimal.valueOf(0.0));
                feeDetailMap.put("submitDate",null);
                feeDetailMap.put("month_"+(k+1),(k+1));
                feeDetailMap.put("fineAmount",BigDecimal.valueOf(0.0));
                feeDetailMap.put("academicStudent", student);
                feeDetailMap.put("blankData",1);
                depositedFeeList.add(feeDetailMap);
            }
            return depositedFeeList;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

}
