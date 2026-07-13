package com.smsweb.sms.config;

import com.smsweb.sms.models.Users.Roles;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.permission.AppScreen;
import com.smsweb.sms.repositories.permission.AppScreenRepository;
import com.smsweb.sms.repositories.permission.UserPermissionRepository;
import com.smsweb.sms.repositories.users.RoleRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Runs at startup.
 *
 * 1. initializeRoles  — seeds Roles + default super_admin user (unchanged)
 * 2. seedScreens       — seeds ALL AppScreen rows that drive the permission matrix.
 *
 * Screen-key naming convention:
 *   MODULE_ENTITY_ACTION
 *   MODULE  : STUDENT | FEES | EMPLOYEE | ADMIN | GLOBAL | MESSAGE
 *   ACTION  : LIST | VIEW | ADD | EDIT | DELETE | PRINT | CANCEL | REPORT | ASSIGN
 *
 * Every key used here MUST match the value used in:
 *   - @CheckAccess(screen="...", type=AccessType.xxx)  on controller methods
 *   - sms:access="...:ACTION"                          on HTML action buttons
 */
@Configuration
public class RoleInitializer {

    // ── 1. Roles + default super-admin user ──────────────────────────────────

    @Bean
    public CommandLineRunner initializeRoles(RoleRepository roleRepository,
                                             UserRepository userRepository,
                                             PasswordEncoder passwordEncoder) {
        return args -> {
            ensureRole(roleRepository, "ROLE_ACCOUNTENT");
            ensureRole(roleRepository, "ROLE_TEACHER");
            ensureRole(roleRepository, "ROLE_STAFF");
            ensureRole(roleRepository, "ROLE_STUDENT");
            ensureRole(roleRepository, "ROLE_ADMIN");
            ensureRole(roleRepository, "ROLE_SUPERADMIN");

            Roles superAdminRole = roleRepository.findByName("ROLE_SUPERADMIN");

            if (userRepository.findByUsername("super_admin") == null) {
                UserEntity superAdmin = new UserEntity();
                superAdmin.setUsername("super_admin");
                superAdmin.setPassword(passwordEncoder.encode("password"));
                superAdmin.setEmail("anoopmonu02@gmail.com");
                superAdmin.setEnabled(true);
                superAdmin.getRoles().add(superAdminRole);
                userRepository.save(superAdmin);
            }
        };
    }

    private void ensureRole(RoleRepository repo, String name) {
        if (repo.findByName(name) == null) {
            repo.save(new Roles(name));
        }
    }

    // ── 2. Screen seed ───────────────────────────────────────────────────────

    @Bean
    public CommandLineRunner seedScreens(AppScreenRepository screenRepo,
                                         UserPermissionRepository permRepo) {
        return args -> {

            // ── Migrate: consolidate 4 legacy Employee screens → 1 EMPLOYEE screen ──
            // Remove old split screens so the permission matrix shows a single row.
            for (String oldKey : new String[]{"EMPLOYEE_LIST", "EMPLOYEE_ADD", "EMPLOYEE_EDIT", "EMPLOYEE_DELETE"}) {
                screenRepo.findByScreenKey(oldKey).ifPresent(old -> {
                    permRepo.deleteByScreenId(old.getId());   // FK-safe: remove permission rows first
                    screenRepo.delete(old);
                });
            }

            // ════════════════════════════════════════════════════════════════
            // MODULE: STUDENT
            // ════════════════════════════════════════════════════════════════

            // Student registration list page
            seed(screenRepo, "Student", "Student List",
                    "STUDENT_LIST",
                    "View the list of all active students");

            // Add new student form + save
            seed(screenRepo, "Student", "Add Student",
                    "STUDENT_ADD",
                    "Open add-student form and save a new student");

            // Edit student personal details form + save
            seed(screenRepo, "Student", "Edit Student",
                    "STUDENT_EDIT",
                    "Open edit-student form and update personal details");

            // View student full profile (show-student page)
            seed(screenRepo, "Student", "View Student Profile",
                    "STUDENT_VIEW",
                    "View full student profile details");

            // Soft-delete (deactivate) a student
            seed(screenRepo, "Student", "Delete Student",
                    "STUDENT_DELETE",
                    "Soft-delete (deactivate) a student record");

            // Inactive students list + re-activate
            seed(screenRepo, "Student", "Inactive Student List",
                    "STUDENT_INACTIVE_LIST",
                    "View list of inactive/deleted students");

            // Re-activate a soft-deleted student
            seed(screenRepo, "Student", "Activate Student",
                    "STUDENT_ACTIVATE",
                    "Re-activate a previously deleted student");

            // Assign SR / board roll numbers to a class
            seed(screenRepo, "Student", "Assign SR Number",
                    "STUDENT_ASSIGN_SR",
                    "Assign SR and board roll numbers to students");

            // Edit Grade / Section reassignment (bulk)
            seed(screenRepo, "Student", "Edit Grade / Section",
                    "STUDENT_EDIT_GRADE",
                    "Reassign students to a different grade or section");

            // Update Aadhaar numbers
            seed(screenRepo, "Student", "Update Aadhaar Detail",
                    "STUDENT_EDIT_AADHAR",
                    "Update Aadhaar number for students");

            // Student lookup / cross-year search
            seed(screenRepo, "Student", "Search Student",
                    "STUDENT_SEARCH",
                    "Search students across all academic years");

            // Session-wise student count report
            seed(screenRepo, "Student", "Student Report (Session)",
                    "STUDENT_REPORT_SESSION",
                    "View session-wise total student count report");

            // Grade-wise student list report
            seed(screenRepo, "Student", "Student Report (Grade)",
                    "STUDENT_REPORT_GRADE",
                    "View grade-wise student list report");

            // ID Card print for a class-section
            seed(screenRepo, "Student", "ID Card Print",
                    "STUDENT_ID_CARD",
                    "Print ID cards for students by medium, grade and section");

            // Grade-wise bulk photo download (images + matching Excel sheet, zipped)
            seed(screenRepo, "Student", "Grade-wise Images Download",
                    "STUDENT_GRADEWISE_IMAGE_DOWNLOAD",
                    "Download student photos and a matching Excel sheet for a medium/grade/section, bundled as a zip");

            // Board Registration data prep for Class 9 (government-format export)
            seed(screenRepo, "Student", "Board Registration (Class 9)",
                    "STUDENT_BOARD_REGISTRATION_CLASS9",
                    "Preview and export Class 9 board registration data in the government-required column format");

            // Birth certificate — search one student, print bilingual certificate
            seed(screenRepo, "Student", "Birth Certificate",
                    "STUDENT_BIRTH_CERTIFICATE",
                    "Search a student and print their birth certificate (English + regional language)");

            // ── Attendance ───────────────────────────────────────────────────

            // View attendance summary dashboard
            seed(screenRepo, "Student", "Attendance Summary",
                    "STUDENT_ATTENDANCE_VIEW",
                    "View student attendance summary by class");

            // Mark daily attendance for a class
            seed(screenRepo, "Student", "Mark Attendance",
                    "STUDENT_ATTENDANCE_MARK",
                    "Submit daily attendance for a class");

            // View attendance monthly report
            seed(screenRepo, "Student", "View Attendance Report",
                    "STUDENT_ATTENDANCE_REPORT",
                    "View monthly attendance report for a class");

            // Exam results
            seed(screenRepo, "Student", "Exam Results",
                    "STUDENT_EXAM_RESULT",
                    "View student exam results by exam and grade");

            // ── Sibling Group ────────────────────────────────────────────────

            seed(screenRepo, "Student", "Sibling Group List",
                    "SIBLING_LIST",
                    "View sibling group list");

            seed(screenRepo, "Student", "Add Sibling Group",
                    "SIBLING_ADD",
                    "Create a new sibling group");

            seed(screenRepo, "Student", "View Sibling Group",
                    "SIBLING_VIEW",
                    "View sibling group details");

            seed(screenRepo, "Student", "Delete Sibling Group",
                    "SIBLING_DELETE",
                    "Delete a sibling group");

            seed(screenRepo, "Student", "Assign Sibling Discount",
                    "SIBLING_DISCOUNT_ASSIGN",
                    "Assign discount to a sibling group");

            // ── Student Discount ─────────────────────────────────────────────

            seed(screenRepo, "Student", "Student Discount List",
                    "STUDENT_DISCOUNT_LIST",
                    "View assigned discounts for students");

            seed(screenRepo, "Student", "Assign Student Discount",
                    "STUDENT_DISCOUNT_ASSIGN",
                    "Assign a discount to a student");

            seed(screenRepo, "Student", "Delete Student Discount",
                    "STUDENT_DISCOUNT_DELETE",
                    "Remove a discount assigned to a student");

            seed(screenRepo, "Student", "Discount List Report (Session)",
                    "STUDENT_DISCOUNT_REPORT",
                    "View session-wise student discount report");

            // ════════════════════════════════════════════════════════════════
            // MODULE: FEES
            // ════════════════════════════════════════════════════════════════

            seed(screenRepo, "Fees", "Fee Submission Form",
                    "FEE_SUBMIT",
                    "Open fee submission form and collect fees from a student");

            seed(screenRepo, "Fees", "Fee Receipt Print",
                    "FEE_RECEIPT_PRINT",
                    "Search and print fee receipts");

            seed(screenRepo, "Fees", "Fee Reminder",
                    "FEE_REMINDER",
                    "View pending fee reminder list");

            seed(screenRepo, "Fees", "Fee Cancellation",
                    "FEE_CANCEL",
                    "Cancel a submitted fee payment");

            seed(screenRepo, "Fees", "Collect Balance",
                    "PENDING_BALANCE_SUBMIT",
                    "Collect pending balance amount from student");

            // ── Fee Reports ──────────────────────────────────────────────────

            seed(screenRepo, "Fees", "User-wise Collection Report",
                    "FEE_REPORT_USER_WISE",
                    "View fee collection report grouped by user/accountant");

            seed(screenRepo, "Fees", "My Collection Report",
                    "FEE_REPORT_OWN_COLLECTION",
                    "View own fee collection report (self-service, logged-in user only)");

            seed(screenRepo, "Fees", "Fee Submitted (By Grade)",
                    "FEE_REPORT_GRADE_WISE",
                    "View total fee submitted report grouped by grade");

            seed(screenRepo, "Fees", "Total Fee Submitted",
                    "FEE_REPORT_TOTAL_SUBMITTED",
                    "View total fee submitted detail report");

            seed(screenRepo, "Fees", "Pending Fee Report",
                    "FEE_REPORT_PENDING",
                    "View total pending fee report");

            seed(screenRepo, "Fees", "Cancelled Fee List",
                    "FEE_REPORT_CANCELLED",
                    "View list of cancelled fee payments");

            seed(screenRepo, "Fees", "Total Deposited Fee",
                    "FEE_REPORT_DEPOSITED",
                    "View total deposited fee report");

            seed(screenRepo, "Fees", "Grade-wise Income Report",
                    "FEE_REPORT_GRADEWISE_INCOME",
                    "View grade-wise fee income report");

            seed(screenRepo, "Fees", "Head-wise Collection Summary",
                    "FEE_REPORT_HEAD_WISE",
                    "View head-wise fee collection summary");

            // ════════════════════════════════════════════════════════════════
            // MODULE: EMPLOYEE
            // ════════════════════════════════════════════════════════════════

            // Unified Employee screen — VIEW / CREATE / EDIT / DELETE are the four AccessTypes
            seed(screenRepo, "Employee", "Employee Management",
                    "EMPLOYEE",
                    "View, add, edit, delete employee records");

            // ════════════════════════════════════════════════════════════════
            // MODULE: ADMIN — Global Settings (Universal master data)
            // ════════════════════════════════════════════════════════════════

            seed(screenRepo, "Global Settings", "Medium",
                    "GLOBAL_MEDIUM",
                    "View, add, edit, delete medium master data");

            seed(screenRepo, "Global Settings", "Grade",
                    "GLOBAL_GRADE",
                    "View, add, edit, delete grade master data");

            seed(screenRepo, "Global Settings", "Section",
                    "GLOBAL_SECTION",
                    "View, add, edit, delete section master data");

            seed(screenRepo, "Global Settings", "Category",
                    "GLOBAL_CATEGORY",
                    "View, add, edit, delete category master data");

            seed(screenRepo, "Global Settings", "Cast",
                    "GLOBAL_CAST",
                    "View, add, edit, delete cast master data");

            seed(screenRepo, "Global Settings", "Bank",
                    "GLOBAL_BANK",
                    "View, add, edit, delete bank master data");

            seed(screenRepo, "Global Settings", "Fee Head",
                    "GLOBAL_FEEHEAD",
                    "View, add, edit, delete fee head master data");

            seed(screenRepo, "Global Settings", "Discount Head",
                    "GLOBAL_DISCOUNTHEAD",
                    "View, add, edit, delete discount head master data");

            seed(screenRepo, "Global Settings", "Fine Head",
                    "GLOBAL_FINEHEAD",
                    "View, add, edit, delete fine head master data");

            // ════════════════════════════════════════════════════════════════
            // MODULE: ADMIN — Admin Configuration
            // ════════════════════════════════════════════════════════════════

            seed(screenRepo, "Admin", "Academic Year",
                    "ADMIN_ACYEAR",
                    "View, add and edit academic years");

            seed(screenRepo, "Admin", "Month Mapping",
                    "ADMIN_MONTH_MAP",
                    "Generate and manage month mappings for a session");

            seed(screenRepo, "Admin", "Fee Date",
                    "ADMIN_FEEDATE",
                    "Add and delete fee due dates");

            seed(screenRepo, "Admin", "Fine",
                    "ADMIN_FINE",
                    "View, add, edit, delete fine configuration");

            seed(screenRepo, "Admin", "Fee-Grade Mapping",
                    "ADMIN_FEE_CLASS",
                    "Map fee heads to grades (fee-class map)");

            seed(screenRepo, "Admin", "Fee-Month Mapping",
                    "ADMIN_FEE_MONTH",
                    "Map fees to months (fee-month map)");

            seed(screenRepo, "Admin", "Discount-Grade Mapping",
                    "ADMIN_DISCOUNT_CLASS",
                    "Map discount heads to grades");

            seed(screenRepo, "Admin", "Discount-Month Mapping",
                    "ADMIN_DISCOUNT_MONTH",
                    "Map discounts to months");

            seed(screenRepo, "Admin", "Full-Payment Discount",
                    "ADMIN_FULL_PAYMENT",
                    "Configure full-payment discount rules");

            seed(screenRepo, "Admin", "Holiday Calendar",
                    "ADMIN_HOLIDAY",
                    "Add and delete school holidays");

            seed(screenRepo, "Admin", "Examination",
                    "ADMIN_EXAM",
                    "View, add, delete examination records");

            seed(screenRepo, "Admin", "Examination Date / Details",
                    "ADMIN_EXAM_DATE",
                    "Add and delete examination schedule details");

            seed(screenRepo, "Admin", "Report Settings (Select Columns)",
                    "ADMIN_REPORT_SETTINGS",
                    "Configure which columns appear in reports");

            // ── User / Role management (ADMIN/SUPERADMIN only) ──────────────

            seed(screenRepo, "Admin", "Role-User Mapping",
                    "ADMIN_USERROLE",
                    "Assign roles to users");

            seed(screenRepo, "Admin", "User Permissions",
                    "ADMIN_USER_PERMISSIONS",
                    "Set fine-grained action permissions for each user");

            // ── School / Customer (SUPERADMIN only — shown for completeness) ─

            seed(screenRepo, "Admin", "Customer Management",
                    "ADMIN_CUSTOMER",
                    "View, add, edit, delete customer (school group) records");

            seed(screenRepo, "Admin", "School / Branch Management",
                    "ADMIN_SCHOOL",
                    "View, add, edit, delete school / branch records");

            seed(screenRepo, "Admin", "Student Regional-Language Details",
                    "ADMIN_STUDENT_REGIONAL",
                    "Download/upload regional-language (e.g. Hindi) name & address details for students");

            // ════════════════════════════════════════════════════════════════
            // MODULE: MESSAGE / COMMUNICATION
            // ════════════════════════════════════════════════════════════════

            seed(screenRepo, "Message", "Send Message / Notification",
                    "MESSAGE_SEND",
                    "Send messages and notifications to students");

            seed(screenRepo, "Message", "View Notifications",
                    "MESSAGE_VIEW",
                    "View sent notification history and replies");
        };
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private void seed(AppScreenRepository repo,
                      String module, String screenName,
                      String screenKey, String description) {
        if (repo.findByScreenKey(screenKey).isEmpty()) {
            repo.save(new AppScreen(module, screenName, screenKey, description));
        }
    }
}