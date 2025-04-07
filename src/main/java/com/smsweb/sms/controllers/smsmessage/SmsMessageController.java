package com.smsweb.sms.controllers.smsmessage;

import com.smsweb.sms.models.messaging.SmsConversation;
import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.services.globalaccess.DropdownService;
//import com.smsweb.sms.services.smsmessage.SmsMessageService;
import com.smsweb.sms.services.smsmessage.SmsMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;

@Controller
@RequestMapping("/message")
public class SmsMessageController {

    //@Autowired
    //private SmsMessageService messageService;

    private final DropdownService dropdownService;
    private final SmsMessageService smsMessageService;

    //, SmsMessageService messageService
    public SmsMessageController(DropdownService dropdownService, SmsMessageService smsMessageService) {
        this.dropdownService = dropdownService;
        //this.messageService = messageService;
        this.smsMessageService = smsMessageService;
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

    @GetMapping("/getSmsMessagesByStudent/{studentId}")
    public ResponseEntity<List<SmsMessage>> getSmsMessagesByStudent(@PathVariable Long studentId) {
        List<SmsMessage> messages = smsMessageService.getMessagesByStudentId(studentId);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/getSmsConversationsByMessage/{messageId}")
    public ResponseEntity<List<Map<String, Object>>> getSmsConversationsByMessage(@PathVariable Long messageId) {
        Optional<SmsMessage> smsMessageOpt = smsMessageService.findById(messageId);
        if (smsMessageOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<SmsConversation> conversations = smsMessageService.findSmsConversationBySmsMessageId(messageId);

        // Build simplified response for JSON
        List<Map<String, Object>> response = conversations.stream().map(conv -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", conv.getId());
            map.put("message", conv.getContent());
            map.put("initiatedBy", conv.getInitiatedBy());
            map.put("sentAt", conv.getSentAt());
            return map;
        }).toList();

        return ResponseEntity.ok(response);
    }
}

