package com.smsweb.sms.controllers.smsmessage;

import com.smsweb.sms.models.messaging.SmsConversation;
import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.services.globalaccess.DropdownService;
//import com.smsweb.sms.services.smsmessage.SmsMessageService;
import com.smsweb.sms.services.smsmessage.SmsMessageService;
import com.smsweb.sms.services.users.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    private final UserService userService;

    //, SmsMessageService messageService
    public SmsMessageController(DropdownService dropdownService, SmsMessageService smsMessageService, UserService userService) {
        this.dropdownService = dropdownService;
        //this.messageService = messageService;
        this.smsMessageService = smsMessageService;
        this.userService = userService;
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
            map.put("seen", conv.getSeen());
            return map;
        }).toList();

        return ResponseEntity.ok(response);
    }


    @PostMapping("/sendSmsConversation")
    public ResponseEntity<Map<String, Object>> sendSmsConversation(
            @RequestBody Map<String, Object> payload) {

        // Extract values from the payload
        Long messageId = Long.valueOf(payload.get("messageId").toString());
        String message = (String) payload.get("message");
        String initiatedBy = (String) payload.get("initiatedBy");

        // Validate messageId
        Optional<SmsMessage> smsMessageOpt = smsMessageService.findById(messageId);
        if (smsMessageOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SmsMessage smsMessage = smsMessageOpt.get();

        // Create a new SmsConversation
        SmsConversation conversation = new SmsConversation();
        conversation.setSmsMessage(smsMessage);
        conversation.setContent(message);
        conversation.setInitiatedBy(initiatedBy);
        conversation.setSeen(false);
        conversation.setIsDeleted(false);
        conversation.setSentAt(new Date());
        conversation.setHasAttachment(false);
        conversation.setHaveDocAttachment(false);

        // Save the conversation
        SmsConversation savedConversation = smsMessageService.saveSmsConversation(conversation);

        // Prepare the response
        Map<String, Object> response = new HashMap<>();
        response.put("id", savedConversation.getId());
        response.put("message", savedConversation.getContent());
        response.put("initiatedBy", savedConversation.getInitiatedBy());
        response.put("sentAt", savedConversation.getSentAt());
        response.put("seen", savedConversation.getSeen());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/resolveSmsMessage/{id}")
    public ResponseEntity<Map<String, Object>> resolveSmsMessage(@PathVariable Long id) {
        String updatedBy = userService.getLoggedInUser().getUsername();
        Optional<SmsMessage> smsMessageOpt = smsMessageService.resolveSmsMessage(id, updatedBy);

        if (smsMessageOpt.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "RESOLVED");
            response.put("messageId", smsMessageOpt.get().getId());
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Message not found or already resolved"));
        }
    }




}

