package com.smsweb.sms.controllers.smsmessage;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.dto.SmsNotificationDto;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.messaging.SmsConversation;
import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.Section;
import com.smsweb.sms.services.Employee.EmployeeService;
import com.smsweb.sms.services.globalaccess.DropdownService;
//import com.smsweb.sms.services.smsmessage.SmsMessageService;
import com.smsweb.sms.services.smsmessage.SmsMessageService;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.student.StudentService;
import com.smsweb.sms.services.universal.GradeService;
import com.smsweb.sms.services.universal.SectionService;
import com.smsweb.sms.services.users.UserService;
import jakarta.servlet.http.HttpSession;
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
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class SmsMessageController {

    //@Autowired
    //private SmsMessageService messageService;

    private final DropdownService dropdownService;
    private final SmsMessageService smsMessageService;
    private final UserService userService;
    private final AcademicStudentService academicStudentService;
    private final StudentService studentService;
    private final GradeService gradeService;
    private final SectionService sectionService;
    private final EmployeeService employeeService;

    //, SmsMessageService messageService
    public SmsMessageController(DropdownService dropdownService, SmsMessageService smsMessageService, UserService userService, AcademicStudentService academicStudentService, StudentService studentService, GradeService gradeService, SectionService sectionService, EmployeeService employeeService) {
        this.dropdownService = dropdownService;
        //this.messageService = messageService;
        this.smsMessageService = smsMessageService;
        this.userService = userService;
        this.academicStudentService = academicStudentService;
        this.studentService = studentService;
        this.gradeService = gradeService;
        this.sectionService = sectionService;
        this.employeeService = employeeService;
    }

    // Endpoint for sending a message (only for employees)
    @CheckAccess(screen = "MESSAGE_SEND", type = AccessType.CREATE)
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
    @CheckAccess(screen = "MESSAGE_SEND", type = AccessType.VIEW)
    @GetMapping("/sendMessage")
    public String sendMessage(Model model){
        SimpleDateFormat sf = new SimpleDateFormat("dd/MMM/yyyy");
        model.addAttribute("todayDate", sf.format(new Date()));
        model.addAttribute("page", "datatable");
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        try{

        }catch(Exception e){
            e.printStackTrace();
        }
        return "message/messageSender";
    }

    @CheckAccess(screen = "MESSAGE_VIEW", type = AccessType.VIEW)
    @GetMapping("/getSmsMessagesByStudent/{studentId}")
    public ResponseEntity<List<Map<String, Object>>> getSmsMessagesByStudent(@PathVariable Long studentId) {
        List<SmsMessage> messages = smsMessageService.getMessagesByStudentId(studentId);
        List<Map<String, Object>> leanList = new ArrayList<>();
        for (SmsMessage msg : messages) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", msg.getId());
            m.put("smsHeading", msg.getSmsHeading() != null ? msg.getSmsHeading() : "");
            m.put("sentAt", msg.getCreatedAt()); // JS accesses msg.sentAt
            m.put("createdAt", msg.getCreatedAt());
            m.put("resolution", msg.getResolution() != null ? msg.getResolution() : "");
            m.put("messageType", msg.getMessageType() != null ? msg.getMessageType() : "");
            leanList.add(m);
        }
        return ResponseEntity.ok(leanList);
    }

    @CheckAccess(screen = "MESSAGE_VIEW", type = AccessType.VIEW)
    @GetMapping("/getSmsConversationsByMessage/{messageId}")
    public ResponseEntity<Map<String, Object>> getSmsConversationsByMessage(@PathVariable Long messageId) {
        Optional<SmsMessage> smsMessageOpt = smsMessageService.findById(messageId);
        if (smsMessageOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<SmsConversation> conversations = smsMessageService.findSmsConversationBySmsMessageId(messageId);

        // Prepare main response map
        Map<String, Object> response = new HashMap<>();

        // Add smsHeading if conversations are present
        if (!conversations.isEmpty() && conversations.get(0).getSmsMessage() != null) {
            response.put("smsHeading", conversations.get(0).getSmsMessage().getSmsHeading());
        } else {
            response.put("smsHeading", smsMessageOpt.get().getSmsHeading()); // fallback from parent
        }
        for (SmsConversation conv : conversations) {
            if ("STUDENT".equalsIgnoreCase(conv.getInitiatedBy()) && !conv.getSeen()) {
                conv.setSeen(true);
            }
            smsMessageService.saveAllConversations(conversations);
        }


        // Add conversation list
        List<Map<String, Object>> convoList = conversations.stream().map(conv -> {


            Map<String, Object> map = new HashMap<>();
            map.put("id", conv.getId());
            map.put("message", conv.getContent());
            map.put("initiatedBy", conv.getInitiatedBy());
            map.put("sentAt", conv.getSentAt());
            map.put("seen", conv.getSeen());
            return map;
        }).toList();

        response.put("conversations", convoList);

        return ResponseEntity.ok(response);
    }



    @CheckAccess(screen = "MESSAGE_SEND", type = AccessType.CREATE)
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

    @CheckAccess(screen = "MESSAGE_SEND", type = AccessType.CREATE)
    @PostMapping("/sendNewMessage")
    public ResponseEntity<Map<String, Object>> sendNewMessage(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        try {
            String message = (String) payload.get("message");

            SmsMessage smsMessage;
            SmsConversation conversation = new SmsConversation();
            conversation.setContent(message);

            conversation.setInitiatedBy(SmsConversation.INITIATED_BY_SCHOOL);


            conversation.setSeen(false);
            conversation.setIsDeleted(false);
            conversation.setSentAt(new Date());
            conversation.setHasAttachment(false);
            conversation.setHaveDocAttachment(false);

            String heading = (String) payload.get("heading");
            Long studentId = Long.valueOf(payload.get("studentId").toString());

            Optional<AcademicStudent> studentOpt = academicStudentService.findById(studentId);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid studentId"));
            }

            AcademicStudent student = studentOpt.get();

            smsMessage = new SmsMessage();
            smsMessage.setSmsHeading(heading);
            smsMessage.setCreatedAt(new Date());
            smsMessage.setMessageType(SmsMessage.MESSAGE_TYPE_COMPLAINT);
            smsMessage.setResolution(SmsMessage.RESOLUTION_TYPE_UNRESOLVED);
            smsMessage.setSchool(student.getSchool());
            smsMessage.setCreatedBy(userService.getLoggedInUser());
            smsMessage.setRecipients(Collections.singletonList(student));
            smsMessage.setRecipientType(SmsMessage.RECIPIENT_TYPE_STUDENT);
            smsMessage.setConversations(new ArrayList<>(List.of(conversation)));


            smsMessageService.saveSmsMessage(smsMessage);

            response.put("message", conversation.getContent());
            response.put("initiatedBy", conversation.getInitiatedBy());
            response.put("sentAt", conversation.getSentAt());
            response.put("seen", conversation.getSeen());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace(); // Or use a logger like log.error("Error in sendSmsConversation", e);
            response.put("error", "An unexpected error occurred: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @CheckAccess(screen = "MESSAGE_VIEW", type = AccessType.EDIT)
    @PostMapping("/resolveSmsMessage/{id}")
    public ResponseEntity<Map<String, Object>> resolveSmsMessage(@PathVariable Long id) {
        Optional<SmsMessage> smsMessageOpt = smsMessageService.resolveSmsMessage(id, userService.getLoggedInUser());

        if (smsMessageOpt.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "RESOLVED");
            response.put("messageId", smsMessageOpt.get().getId());
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Message not found or already resolved"));
        }
    }

    @CheckAccess(screen = "MESSAGE_SEND", type = AccessType.CREATE)
    @PostMapping("/sendNewNotification")
    public ResponseEntity<?> sendNotification(@RequestBody Map<String, Object> payload) {
        try {
            // Extract required fields
            String heading = (String) payload.get("heading");
            String message = (String) payload.get("message");
            String messageType = (String) payload.get("messageType");
            String recipientType = (String) payload.get("recipientType");

            if (heading == null || message == null || messageType == null || recipientType == null) {
                return ResponseEntity.badRequest().body("Missing required fields.");
            }

            // Get school using logged-in user
            UserEntity loggedInUser = userService.getLoggedInUser();
            String loggedInUsername = loggedInUser.getUsername();

            School school = employeeService.getLoggedInEmployeeSchool(loggedInUsername)
                    .orElseThrow(() -> new RuntimeException("School not found for Logged In user: " + loggedInUsername));

            // Create and populate SmsMessage
            SmsMessage smsMessage = new SmsMessage();
            smsMessage.setSmsHeading(heading);
            smsMessage.setMessageType(SmsMessage.MESSAGE_TYPE_NOTIFICATION);
            smsMessage.setCreatedAt(new Date());
            smsMessage.setSchool(school);
            smsMessage.setCreatedBy(loggedInUser);
            smsMessage.setResolution(SmsMessage.RESOLUTION_TYPE_UNRESOLVED);

            // Set recipient type early
            switch (recipientType.toUpperCase()) {
                case "CLASS":
                    smsMessage.setRecipientType(SmsMessage.RECIPIENT_TYPE_CLASS);
                    try {
                        Long gradeId = Long.valueOf(payload.get("classId").toString());
                        Long sectionId = Long.valueOf(payload.get("sectionId").toString());

                        Grade grade = gradeService.getGradeById(gradeId)
                                .orElseThrow(() -> new RuntimeException("Grade not found with ID: " + gradeId));
                        Section section = sectionService.getSectionById(sectionId)
                                .orElseThrow(() -> new RuntimeException("Section not found with ID: " + sectionId));

                        smsMessage.setGrade(grade);
                        smsMessage.setSection(section);
                    } catch (NumberFormatException e) {
                        return ResponseEntity.badRequest().body("Invalid classId or sectionId format.");
                    }
                    break;

                case "STUDENT":
                    smsMessage.setRecipientType(SmsMessage.RECIPIENT_TYPE_STUDENT);
                    try {
                        Long studentId = Long.valueOf(payload.get("studentId").toString());

                        AcademicStudent student = academicStudentService.findById(studentId)
                                .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

                        smsMessage.setRecipients(Collections.singletonList(student));
                    } catch (NumberFormatException e) {
                        return ResponseEntity.badRequest().body("Invalid studentId format.");
                    }
                    break;

                default:
                    smsMessage.setRecipientType(SmsMessage.RECIPIENT_TYPE_ALL);
            }

            // Create initial SmsConversation
            SmsConversation conversation = new SmsConversation();
            conversation.setSmsMessage(smsMessage);
            conversation.setContent(message);
            conversation.setSentAt(new Date());
            conversation.setSeen(true);
            conversation.setHasAttachment(false);
            conversation.setHaveDocAttachment(false);
            conversation.setIsDeleted(false);
            conversation.setInitiatedBy(SmsConversation.INITIATED_BY_SCHOOL);

            smsMessage.setConversations(Collections.singletonList(conversation));
            smsMessageService.saveSmsMessage(smsMessage);

            return ResponseEntity.ok("Notification sent successfully.");
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error while sending Notification: " + ex.getMessage());
        }
    }



    @CheckAccess(screen = "MESSAGE_VIEW", type = AccessType.VIEW)
    @GetMapping("/getActivitiesByStudent/{studentId}")
    public ResponseEntity<List<Map<String, Object>>> getActivitiesByStudent(@PathVariable Long studentId) {
        List<SmsMessage> activities = smsMessageService.getActivitiesByStudentId(studentId);
        List<Map<String, Object>> leanList = new ArrayList<>();
        for (SmsMessage msg : activities) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", msg.getId());
            m.put("title", msg.getSmsHeading() != null ? msg.getSmsHeading() : "");
            m.put("createdAt", msg.getCreatedAt());
            // Get description from first conversation
            String description = "";
            if (msg.getConversations() != null && !msg.getConversations().isEmpty()) {
                description = msg.getConversations().get(0).getContent() != null
                        ? msg.getConversations().get(0).getContent() : "";
            }
            m.put("description", description);
            leanList.add(m);
        }
        return ResponseEntity.ok(leanList);
    }

    @CheckAccess(screen = "MESSAGE_SEND", type = AccessType.CREATE)
    @PostMapping("/saveActivity")
    public ResponseEntity<Map<String, Object>> saveActivity(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String title = (String) payload.get("title");
            String description = (String) payload.get("description");
            Long studentId = Long.valueOf(payload.get("studentId").toString());

            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));
            }
            if (description == null || description.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Description is required"));
            }

            Optional<AcademicStudent> studentOpt = academicStudentService.findById(studentId);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid studentId"));
            }
            AcademicStudent student = studentOpt.get();

            SmsConversation conversation = new SmsConversation();
            conversation.setContent(description);
            conversation.setInitiatedBy(SmsConversation.INITIATED_BY_SCHOOL);
            conversation.setSeen(true);
            conversation.setIsDeleted(false);
            conversation.setHasAttachment(false);
            conversation.setHaveDocAttachment(false);

            SmsMessage smsMessage = new SmsMessage();
            smsMessage.setSmsHeading(title);
            smsMessage.setMessageType(SmsMessage.MESSAGE_TYPE_ACTIVITIES);
            smsMessage.setResolution(SmsMessage.RESOLUTION_TYPE_UNRESOLVED);
            smsMessage.setRecipientType(SmsMessage.RECIPIENT_TYPE_STUDENT);
            smsMessage.setSchool(student.getSchool());
            smsMessage.setCreatedBy(userService.getLoggedInUser());
            smsMessage.setCreatedAt(new java.util.Date());
            smsMessage.setRecipients(Collections.singletonList(student));
            smsMessage.setConversations(new ArrayList<>(List.of(conversation)));

            smsMessageService.saveSmsMessage(smsMessage);

            response.put("success", true);
            response.put("id", smsMessage.getId());
            response.put("title", smsMessage.getSmsHeading());
            response.put("description", description);
            response.put("createdAt", smsMessage.getCreatedAt());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error saving activity: " + e.getMessage()));
        }
    }

    @CheckAccess(screen = "MESSAGE_VIEW", type = AccessType.VIEW)
    @GetMapping("/viewNotification")
    public String viewStudentNotifications(Model model) {
        model.addAttribute("page", "datatable");
        model.addAttribute("mediums", dropdownService.getMediums());
        model.addAttribute("grades", dropdownService.getGrades());
        model.addAttribute("sections", dropdownService.getSections());
        model.addAttribute("todayDate", new SimpleDateFormat("dd/MMM/yyyy").format(new Date()));
        return "message/studentNotifications";
    }

    @CheckAccess(screen = "MESSAGE_VIEW", type = AccessType.VIEW)
    @GetMapping("/notifications")
    public ResponseEntity<List<SmsNotificationDto>> getStudentNotifications(@RequestParam(required = false) Long studentId) {
        List<SmsNotificationDto> notifications = new ArrayList<>();
        if (studentId != null) {
            notifications = smsMessageService.getNotificationDtosByStudentId(studentId);
        }
        return ResponseEntity.ok(notifications);
    }

    @CheckAccess(screen = "MESSAGE_SEND", type = AccessType.VIEW)
    @GetMapping("/getStudentDetailForMessage/{id}")
    public ResponseEntity<Map<String, Object>> getStudentDetailForMessage(@PathVariable("id") Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Optional<AcademicStudent> studentOpt = academicStudentService.findById(id);
            if (studentOpt.isPresent()) {
                result.put("student", studentService.toLeanAcademicStudentMap(studentOpt.get()));
            } else {
                result.put("noAcademicStudent", "Student not found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("error", "Error: " + e.getLocalizedMessage());
        }
        return ResponseEntity.ok(result);
    }


}

