package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.helper.FileHandleHelper;
import com.smsweb.sms.models.admin.Customer;
import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.services.admin.CustomerService;
import com.smsweb.sms.services.users.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('ROLE_SUPERADMIN')")
public class CustomerController {
    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);


    private final CustomerService customerService;
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB
    private static final String PIC_FILENAME_FORMAT_PREFIX = "ddMMyyyyhhmmss";
    private final UserService userService;
    private final FileHandleHelper fileHandleHelper;

    @Autowired
    public CustomerController(CustomerService customerService, UserService userService, FileHandleHelper fileHandleHelper){
        this.customerService = customerService;
        this.userService = userService;
        this.fileHandleHelper = fileHandleHelper;
    }

    @CheckAccess(screen = "ADMIN_CUSTOMER", type = AccessType.VIEW)
    @GetMapping("/customer")
    public String getCustomers(Model model){
        log.info("Inside getCustomers");
        List<Customer> customers = customerService.getAllCustomers();
        model.addAttribute("customers",customers);
        model.addAttribute("hasCustomerData", !customers.isEmpty());
        return "admin/customer";
    }
    @CheckAccess(screen = "ADMIN_CUSTOMER", type = AccessType.CREATE)
    @GetMapping("/customer/add")
    public String addCustomerForm(Model model){
        log.info("Inside addCustomerForm");
        model.addAttribute("customer", new Customer());
        model.addAttribute("provinces", customerService.getAllProvinces());
        return "admin/add-customer";
    }

    @ResponseBody
    @CheckAccess(screen = "ADMIN_CUSTOMER", type = AccessType.VIEW)
    @GetMapping("/customer/cities")
    public List<City> getCities(@RequestParam Long provinceId) {
        log.info("Inside getCities");
        return customerService.getAllCitiesByProvince(provinceId);
    }

    @CheckAccess(screen = "ADMIN_CUSTOMER", type = AccessType.CREATE)
    @PostMapping("/customer")
    public String saveCustomer(@Valid @ModelAttribute("customer")Customer customer, BindingResult result, @RequestParam("customerPic")MultipartFile customerPic,
                               Model model, RedirectAttributes ra){
        log.info("Inside saveCustomer");
        if(result.hasErrors()){
            model.addAttribute("provinces", customerService.getAllProvinces());
            return "admin/add-customer";
        }
        SimpleDateFormat sf = new SimpleDateFormat(PIC_FILENAME_FORMAT_PREFIX);
        String registrationNo = sf.format(new Date());
        if(!customerPic.isEmpty()){
            try{
                log.debug("Uploaded customer pic content-type: {}", customerPic.getContentType());
                if (!customerPic.getContentType().startsWith("image/")) {
                    model.addAttribute("provinces", customerService.getAllProvinces());
                    model.addAttribute("picUploadError", "Only image files are allowed.");
                    return "admin/add-customer";
                }

                // Check file size
                if (customerPic.getSize() > MAX_FILE_SIZE) {
                    model.addAttribute("provinces", customerService.getAllProvinces());
                    model.addAttribute("picUploadError", "File size must be less than 2 MB.");
                    return "admin/add-customer";
                }
                boolean proceedFlag = false;
                String imageResponse =  fileHandleHelper.saveImage("customer", customerPic);
                boolean foundImageResponse = (imageResponse!=null && imageResponse!="")?true:false;
                if(foundImageResponse && imageResponse.equalsIgnoreCase("Success_no_image")){
                    proceedFlag = true;
                    customer.setPic(null);
                } else if(foundImageResponse && imageResponse.equalsIgnoreCase("Either image format not supported or size exceeded 2MB.")){
                    throw new FileSizeLimitExceededException("Either image format not supported or size exceeded 2MB.");
                } else if (foundImageResponse && imageResponse.startsWith("Failed to save the image: ")) {
                    throw new FileFormatException(imageResponse);
                } else if (foundImageResponse && imageResponse.equalsIgnoreCase("Specified category not valid")) {
                    throw new RuntimeException(imageResponse);
                } else{
                    customer.setPic(imageResponse);
                    proceedFlag = true;
                }
                log.debug("Customer pic saved: fileName={}, result={}", customerPic.getOriginalFilename(), imageResponse);

            }catch(Exception e){
                model.addAttribute("picUploadError", "Could not upload pic: "+e.getLocalizedMessage());
                model.addAttribute("provinces", customerService.getAllProvinces());
                e.printStackTrace();
                ra.addFlashAttribute("imgerror",e.getLocalizedMessage());
                return "admin/add-customer";
            }
        } else{
            customer.setPic(null);
        }
        customer.setRegistrationNo(registrationNo);
        log.info("Saving new customer, registrationNo={}", registrationNo);
        customer.setCreatedBy(userService.getLoggedInUser());
        customerService.saveCustomer(customer);

        ra.addFlashAttribute("savecustomer", customer);

        return "redirect:/admin/customer";
    }

    @CheckAccess(screen = "ADMIN_CUSTOMER", type = AccessType.EDIT)
    @GetMapping("/customer/edit/{id}")
    public String getEditPage(@PathVariable("id") Long id, Model model){
        log.info("Inside getEditPage");
        Customer customer = customerService.getCustomerById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid customer Id:" + id));
        model.addAttribute("customer", customer);
        model.addAttribute("provinces", customerService.getAllProvinces());
        return "admin/edit-customer";
    }

    @CheckAccess(screen = "ADMIN_CUSTOMER", type = AccessType.EDIT)
    @PostMapping("/customer/{id}")
    public String updateCustomer(@PathVariable("id") Long id, @Valid @ModelAttribute("customer") Customer customer,
                                 @RequestParam("customerPic")MultipartFile customerPic, BindingResult result, Model model, RedirectAttributes ra){
        log.info("Inside updateCustomer");
        if(result.hasErrors()){
            model.addAttribute("provinces", customerService.getAllProvinces());
            return "admin/edit-customer";
        }
        SimpleDateFormat sf = new SimpleDateFormat(PIC_FILENAME_FORMAT_PREFIX);
        String imageFileNameFormat = sf.format(new Date());
        if(!customerPic.isEmpty()){
            try{
                log.debug("Uploaded customer pic content-type: {}", customerPic.getContentType());
                if (!customerPic.getContentType().startsWith("image/")) {
                    model.addAttribute("provinces", customerService.getAllProvinces());
                    model.addAttribute("picUploadError", "Only image files are allowed.");
                    return "admin/add-customer";
                }

                // Check file size
                if (customerPic.getSize() > MAX_FILE_SIZE) {
                    model.addAttribute("provinces", customerService.getAllProvinces());
                    model.addAttribute("picUploadError", "File size must be less than 2 MB.");
                    return "admin/edit-customer";
                }
                boolean proceedFlag = false;
                String imageResponse =  fileHandleHelper.saveImage("customer", customerPic);
                boolean foundImageResponse = (imageResponse!=null && imageResponse!="")?true:false;
                if(foundImageResponse && imageResponse.equalsIgnoreCase("Success_no_image")){
                    proceedFlag = true;
                } else if(foundImageResponse && imageResponse.equalsIgnoreCase("Either image format not supported or size exceeded 2MB.")){
                    throw new FileSizeLimitExceededException("Either image format not supported or size exceeded 2MB.");
                } else if (foundImageResponse && imageResponse.startsWith("Failed to save the image: ")) {
                    throw new FileFormatException(imageResponse);
                } else if (foundImageResponse && imageResponse.equalsIgnoreCase("Specified category not valid")) {
                    throw new RuntimeException(imageResponse);
                } else{
                    customer.setPic(imageResponse);
                    proceedFlag = true;
                }
                log.debug("Customer pic saved: fileName={}, result={}", customerPic.getOriginalFilename(), imageResponse);
                customer.setPic(imageResponse);
            }catch(Exception e){
                model.addAttribute("picUploadError", "Could not upload pic: "+e.getLocalizedMessage());
                model.addAttribute("provinces", customerService.getAllProvinces());
                e.printStackTrace();
                ra.addFlashAttribute("imgerror",e.getLocalizedMessage());
                return "admin/edit-customer";
            }
        } else{
            //customer.setPic(null);
        }
        customer.setUpdatedBy(userService.getLoggedInUser());
        customerService.saveCustomer(customer);

        ra.addFlashAttribute("update-customer", customer);
        return "redirect:/admin/customer";
    }
}
