package com.smsweb.sms.controllers.admin;


import com.smsweb.sms.models.admin.Customer;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.services.admin.SchoolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class SchoolController {

    private final SchoolService schoolService;
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB
    private static final String PIC_FILENAME_FORMAT_PREFIX = "ddMMyyyyhhmmss";
    @Autowired
    public SchoolController(SchoolService schoolService){
        this.schoolService = schoolService;
    }

    @GetMapping("/school")
    public String getSchools(Model model){
        List<School> schools = schoolService.getAllSchools();
        model.addAttribute("schools",schools);
        model.addAttribute("hasSchoolData", !schools.isEmpty());
        return "/admin/school";
    }

    @GetMapping("/school/add")
    public String addSchoolForm(Model model){
        model.addAttribute("school", new School());
        model.addAttribute("provinces", schoolService.getAllProvinces());
        return "/admin/add-school";
    }

    @ResponseBody
    @GetMapping("/school/cities")
    public List<City> getCities(@RequestParam Long provinceId) {
        return schoolService.getAllCitiesByProvince(provinceId);
    }

}
