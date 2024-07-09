package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.admin.Customer;
import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.services.admin.CustomerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
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

@Controller
@RequestMapping("/admin")
public class CustomerController {

    private final CustomerService customerService;
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB

    @Autowired
    public CustomerController(CustomerService customerService){
        this.customerService = customerService;
    }

    @GetMapping("/customer")
    public String getCustomers(Model model){
        List<Customer> customers = customerService.getAllCustomers();
        model.addAttribute("customers",customers);
        model.addAttribute("hasCustomerData", !customers.isEmpty());
        return "/admin/customer";
    }
    @GetMapping("/customer/add")
    public String addCustomerForm(Model model){
        model.addAttribute("customer", new Customer());
        model.addAttribute("provinces", customerService.getAllProvinces());
        return "/admin/add-customer";
    }

    @ResponseBody
    @GetMapping("/customer/cities")
    public List<City> getCities(@RequestParam Long provinceId) {
        return customerService.getAllCitiesByProvince(provinceId);
    }

    @PostMapping("/customer")
    public String saveCustomer(@Valid @ModelAttribute("customer")Customer customer, BindingResult result, @RequestParam("customerPic")MultipartFile customerPic,
                               Model model, RedirectAttributes ra){
        if(result.hasErrors()){
            model.addAttribute("provinces", customerService.getAllProvinces());
            return "/admin/add-customer";
        }
        if(!customerPic.isEmpty()){
            try{
                System.out.println("==== "+customerPic.getContentType());
                if (!customerPic.getContentType().startsWith("image/")) {
                    model.addAttribute("provinces", customerService.getAllProvinces());
                    model.addAttribute("picUploadError", "Only image files are allowed.");
                    return "/admin/add-customer";
                }

                // Check file size
                if (customerPic.getSize() > MAX_FILE_SIZE) {
                    model.addAttribute("provinces", customerService.getAllProvinces());
                    model.addAttribute("picUploadError", "File size must be less than 2 MB.");
                    return "/admin/add-customer";
                }
                File imageFile = new ClassPathResource("static/images").getFile();
                Path path = Paths.get(imageFile.getAbsolutePath() + File.separator + customerPic.getOriginalFilename());
                System.out.println("path: "+path);
                long l = Files.copy(customerPic.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("File Name: "+customerPic.getOriginalFilename()+"  L: "+l);
                customer.setPic(customerPic.getOriginalFilename());
            }catch(Exception e){
                model.addAttribute("picUploadError", "Could not upload pic: "+e.getLocalizedMessage());
                model.addAttribute("provinces", customerService.getAllProvinces());
                e.printStackTrace();
                return "/admin/add-customer";
            }
        } else{
            customer.setPic(null);
        }
        SimpleDateFormat sf = new SimpleDateFormat("ddMMyyyyhhmmss");
        String registrationNo = sf.format(new Date());
        customer.setRegistrationNo(registrationNo);
        System.out.println("customer: "+customer);
        customerService.saveCustomer(customer);

        ra.addFlashAttribute("savecustomer", customer);

        return "redirect:/admin/customer";
    }
}
