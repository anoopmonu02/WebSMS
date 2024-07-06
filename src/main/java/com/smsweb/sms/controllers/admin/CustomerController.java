package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.admin.Customer;
import com.smsweb.sms.services.admin.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class CustomerController {

    private final CustomerService customerService;

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
}
