package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.SiblingGroup;
import com.smsweb.sms.services.student.SiblingGroupService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Controller
@RequestMapping("/sibling")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class SiblingGroupController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(SiblingGroupController.class);

    private SiblingGroupService siblingGroupService;

    @Autowired
    public SiblingGroupController(SiblingGroupService siblingGroupService){
        this.siblingGroupService = siblingGroupService;
    }

    @CheckAccess(screen = "SIBLING_LIST", type = AccessType.VIEW)
    @GetMapping("/sibling-group")
    public String getSiblingGroupListForm(Model model){
        log.info("Inside getSiblingGroupListForm");
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
        List<SiblingGroup> siblingGroupList = siblingGroupService.getAllSiblingGroups(school.getId(), academicYear.getId());
        model.addAttribute("siblingGroups", siblingGroupList);
        model.addAttribute("hasSiblingGroup", !siblingGroupList.isEmpty());
        return "student/siblinggrouplist";
    }

    @CheckAccess(screen = "SIBLING_ADD", type = AccessType.CREATE)
    @GetMapping("/sibling-group/add")
    public String getAddSiblingForm(Model model){
        log.info("Inside getAddSiblingForm");
        return "student/add-siblinggroup";
    }

    @CheckAccess(screen = "SIBLING_ADD", type = AccessType.CREATE)
    @PostMapping("/savesiblinggroup")
    public String saveSiblingGroup(HttpServletRequest request, RedirectAttributes redirectAttributes, Model model){
        log.info("Inside saveSiblingGroup");
        try{
            Map paramMap = request.getParameterMap();
            log.debug("saveSiblingGroup request params: {}", paramMap.keySet());
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            Map responseMap = siblingGroupService.save(paramMap, school, academicYear);
            if(responseMap.containsKey("STUDENT_EXIST")){
                // Student already in another sibling group — block and show error on the add form
                redirectAttributes.addFlashAttribute("error", responseMap.get("STUDENT_EXIST"));
                return "redirect:/sibling/sibling-group/add";
            }
            if(responseMap.containsKey("error")){
                redirectAttributes.addFlashAttribute("error", responseMap.get("error"));
                return "redirect:/sibling/sibling-group/add";
            }
            if(responseMap.containsKey("NOT_SAVED")){
                redirectAttributes.addFlashAttribute("error", "Error in saving sibling group.");
                return "redirect:/sibling/sibling-group/add";
            }
            if(responseMap.containsKey("siblingGroup")){
                List<SiblingGroup> siblingGroupList = (List<SiblingGroup>) responseMap.get("siblingGroup");
                if(siblingGroupList==null || siblingGroupList.isEmpty()){
                    redirectAttributes.addFlashAttribute("error", "Error in sibling group creation.");
                    return "redirect:/sibling/sibling-group/add";
                }
            }
            redirectAttributes.addFlashAttribute("success", "Group saved successfully.");
        }catch(Exception e){
            e.printStackTrace();
        }
        return "redirect:/sibling/sibling-group";
    }

    @CheckAccess(screen = "SIBLING_VIEW", type = AccessType.VIEW)
    @GetMapping("/sibling-group/show/{id}")
    public String showgroupdetail(@PathVariable("id")Long id, Model model, RedirectAttributes redirectAttributes){
        log.info("Inside showgroupdetail");
        SiblingGroup group = siblingGroupService.getSiblingGroupDetail(id).orElse(null);
        if(group!=null){
            model.addAttribute("siblinggroup", group);
        } else{
            redirectAttributes.addFlashAttribute("error", "Sibling group detail not found.");
            return "redirect:/sibling/sibling-group";
        }
        return "student/show-siblinggroup";
    }

    @CheckAccess(screen = "SIBLING_DELETE", type = AccessType.DELETE)
    @GetMapping("/sibling-group/delete/{id}")
    public String deletegroup(@PathVariable("id")String id, Model model, RedirectAttributes redirectAttributes){
        log.info("Inside deletegroup");
        String msg = siblingGroupService.deleteSiblingGroup(Long.valueOf(id));
        if(msg.contains("success")){
            redirectAttributes.addFlashAttribute("success","Sibling-group deleted successfully.");
        } else if(msg.contains("Error")){
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            List<SiblingGroup> siblingGroupList = siblingGroupService.getAllSiblingGroups(school.getId(), academicYear.getId());
            model.addAttribute("siblingGroups", siblingGroupList);
            model.addAttribute("hasSiblingGroup", !siblingGroupList.isEmpty());
            redirectAttributes.addFlashAttribute("error",msg);
        }
        return "redirect:/sibling/sibling-group";
    }
}
