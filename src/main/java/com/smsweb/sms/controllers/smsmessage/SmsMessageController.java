package com.smsweb.sms.controllers.smsmessage;

import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.services.globalaccess.DropdownService;
//import com.smsweb.sms.services.smsmessage.SmsMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/messages")
public class SmsMessageController {

    //@Autowired
    //private SmsMessageService messageService;

    private final DropdownService dropdownService;

    //, SmsMessageService messageService
    public SmsMessageController(DropdownService dropdownService) {
        this.dropdownService = dropdownService;
        //this.messageService = messageService;
    }

    // Endpoint for sending a message (only for employees)
    @PostMapping("/send")
    @PreAuthorize("hasRole('EMPLOYEE')") // Ensure only employees can send messages
    public ResponseEntity<String> sendMessage(
            @RequestParam Long senderId,
            @RequestParam Long recipientId,
            @RequestParam String content
    ) {
        //messageService.sendMessage(senderId, recipientId, content);
        return ResponseEntity.ok("Message sent successfully!");
    }
    @GetMapping("/sendMessage")
    public String sendMessage(Model model){
        SimpleDateFormat sf = new SimpleDateFormat("dd/MMM/yyyy");
        model.addAttribute("todayDate", sf.format(new Date()));
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        try{

        }catch(Exception e){
            e.printStackTrace();
        }
        return "message/messageSender";
    }
}

