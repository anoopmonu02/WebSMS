package com.smsweb.sms.controllers;

import com.smsweb.sms.config.AcademicYearHolder;
import com.smsweb.sms.config.SchoolHolder;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.Holiday;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.services.Employee.EmployeeService;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.HolidayService;
import com.smsweb.sms.services.admin.SchoolService;
import com.smsweb.sms.services.fees.FeeSubmissionService;
import com.smsweb.sms.services.student.StudentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final HttpSession session;
    private final AcademicyearService academicyearService;
    private final SchoolService schoolService;
    private final AcademicYearHolder academicYearHolder;
    private final SchoolHolder schoolHolder;
    private final HolidayService holidayService;
    private final StudentService studentService;
    private final EmployeeService employeeService;
    private final FeeSubmissionService feeSubmissionService;


    public HomeController(HttpSession httpSession, AcademicyearService academicyearService, SchoolService schoolService, AcademicYearHolder academicYearHolder, SchoolHolder schoolHolder, HolidayService holidayService, StudentService studentService, EmployeeService employeeService, FeeSubmissionService feeSubmissionService) {
        this.session = httpSession;
        this.academicyearService = academicyearService;
        this.schoolService = schoolService;
        this.academicYearHolder = academicYearHolder;
        this.schoolHolder = schoolHolder;
        this.holidayService = holidayService;
        this.studentService = studentService;
        this.employeeService = employeeService;
        this.feeSubmissionService = feeSubmissionService;
    }

    @GetMapping("/dashboard")
    public String index(HttpSession session, Model model){
        School school = (School) session.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)session.getAttribute("activeAcademicYear");

        System.out.println("school--"+school);
        model.addAttribute("school", school);
        /*schoolHolder.setCurrentSchool(school);
        academicYearHolder.setCurrentAcademicYear(academicyearService.getCurrentAcademicYear(school.getId()));*/

        //fetching holidays & Events
        List<Holiday> holidayList = holidayService.getAllHolidayStartsFromToday(academicYear.getId(), school.getId());
        model.addAttribute("holidays", holidayList);
        model.addAttribute("isHoliDays", !holidayList.isEmpty());

        //fetching total students
        int totalStudents = studentService.getAllStudentsCount(school.getId(), academicYear.getId());
        model.addAttribute("totalStudents", totalStudents>0? totalStudents:0);

        /*int deleteStudents = studentService.getAllInactiveStudentsCount(school.getId(), academicYear.getId());
        model.addAttribute("deleteStudents", deleteStudents>0?deleteStudents:0);

        int totalEmployees = employeeService.getAllActiveEmployeesCount(school.getId());
        model.addAttribute("totalEmployees", totalEmployees>0?totalEmployees:0);*/

        //Fetch Grade-wise absent student list
        //List<Map<String, Object>> attendanceList = studentService.getAttendanceDetailsCollectedByClass(school.getId(), academicYear.getId());
        List<Map<String, Object>> attendanceList = studentService.getAbsentSummaryGradewise(school.getId(), academicYear.getId());

        int totalPresentCount = 0;
        for (Map<String, Object> summary : attendanceList) {
            Object absentObj = summary.get("presentSummaryCount");
            if (absentObj != null) {
                totalPresentCount += ((Number) absentObj).intValue();
            }
        }
        model.addAttribute("attendances", attendanceList);
        model.addAttribute("isAttendance", !attendanceList.isEmpty());
        int totalAbsent = totalStudents - totalPresentCount;
        model.addAttribute("totalAbsent",totalAbsent);
        model.addAttribute("totalPresent",totalPresentCount);


        //Fetch Upcoming Events

        //Fetch Aadhaar & SR details of students for chart
        //count of - Total Aadhaar/No Aadhaar, total absent gender-wise, Total SR/No SR, Gender Ratio
        Map chartDataMap = studentService.getPieChartData(school.getId(), academicYear.getId(), totalStudents);
        model.addAttribute("totalGirlsCount", chartDataMap.get("totalGirlsCount"));
        model.addAttribute("totalBoysCount", chartDataMap.get("totalBoysCount"));
        model.addAttribute("totalNPCount", chartDataMap.get("totalNPCount"));
        model.addAttribute("totalAadhaar", chartDataMap.get("totalAadhaarCount"));
        model.addAttribute("totalSR", chartDataMap.get("totalSRCount"));

        model.addAttribute("totalGirlsAbsentCount", chartDataMap.get("totalGirlsAbsent"));
        model.addAttribute("totalBoysAbsentCount", chartDataMap.get("totalBoysAbsent"));
        model.addAttribute("totalNPAbsentCount", chartDataMap.get("totalNPAbsent"));

        //Fetch upcoming birthdays
        List<String[]> stuDobList = studentService.getComingBirthDays(school.getId(), academicYear.getId());
        List<String[]> empDobList = employeeService.getComingBirthDays(school.getId(), academicYear.getId());
        List<String[]> combinedList = new ArrayList<>();
        combinedList.addAll(stuDobList);
        combinedList.addAll(empDobList);
        model.addAttribute("studentsDobUpcoming", combinedList);
        model.addAttribute("isStudentsDobUpcoming", !combinedList.isEmpty());
        //Fetch month-wise collection

        //Fetch fee submitted today
        BigDecimal totalFeeSubmittedAmount = feeSubmissionService.getTodayFeeCollection(school.getId(), academicYear.getId());
        model.addAttribute("totalFeeSubmittedToday", totalFeeSubmittedAmount.toString());

        return "index";
    }


    public boolean isSuperAdmin(){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName(); // Get logged-in username
            if(username.equalsIgnoreCase("super_admin")){
                return true;
            }
            /*// Get full UserDetails object if needed
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) principal;
                // Access other user details if required
                if(userDetails.getUsername().equalsIgnoreCase("super_admin")){
                    return true;
                }
            }*/
        }catch(Exception e){
            e.printStackTrace();
        }
        return false;
    }

    private Date convertToDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }



}
