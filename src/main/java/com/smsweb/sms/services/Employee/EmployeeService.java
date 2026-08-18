package com.smsweb.sms.services.Employee;

import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.helper.FileHandleHelper;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.Roles;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import com.smsweb.sms.repositories.users.RoleRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import com.smsweb.sms.services.users.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class EmployeeService {
    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);


    private final FileHandleHelper fileHandleHelper;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    private static final SecureRandom RNG = new SecureRandom();
    // Excludes ambiguous characters (0/O, 1/l/I) — same convention as
    // FamilyAccountService's temp password generator (Mobile Users screen).
    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    public EmployeeService(FileHandleHelper fileHandleHelper, EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder, UserService userService, RoleRepository roleRepository, UserRepository userRepository) {
        this.fileHandleHelper = fileHandleHelper;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Employee saveEmployee(Employee employee, MultipartFile logo, String fileNameOrSchoolCode, Employee existingEmployee) throws IOException {
        log.info("Inside saveEmployee");
        String imageResponse = fileHandleHelper.saveImage("employee", logo);
        boolean proceedFlag = false;

        // UserEntity setup (new or existing)
        UserEntity userEntity;

        if (existingEmployee != null) {
            // Use the existing UserEntity from the existing employee
            userEntity = existingEmployee.getUserEntity();

            // Set existing employee details
            employee.setEmployeeCode(existingEmployee.getEmployeeCode());
            if (existingEmployee.getPic() != null && !existingEmployee.getPic().isEmpty()) {
                employee.setPic(existingEmployee.getPic());
            }

            // Copy over username and password from existing UserEntity

            userEntity.setEmail(employee.getUserEntity().getEmail());
            userEntity.setUsername(existingEmployee.getUserEntity().getUsername());
            userEntity.setPassword(existingEmployee.getUserEntity().getPassword());
            UserEntity updateUserEntity = userRepository.save(userEntity);
            employee.setUserEntity(updateUserEntity);
            employee.setUpdatedBy(userService.getLoggedInUser());
            // Ensure other necessary fields from UserEntity are retained
        } else {
            // Generate employee code for new employee
            String empCode = "ERN-" + fileNameOrSchoolCode;
            employee.setEmployeeCode(empCode);

            // Create new UserEntity for new employee
            userEntity = new UserEntity();

            // Generate username and password
            userEntity = generateUsernameAndPassword(employee, userEntity);
            userEntity.setEmail(employee.getUserEntity().getEmail());
            userEntity.setEnabled(true);
            UserEntity empEnt = userRepository.save(userEntity);
            // Assign UserEntity to the new employee
            employee.setUserEntity(empEnt);
            employee.setCreatedBy(userService.getLoggedInUser());
        }

        // Handle image upload logic
        if (imageResponse == null || imageResponse.isEmpty()) {
            proceedFlag = true; // No image or error during upload
        } else if ("Success_no_image".equalsIgnoreCase(imageResponse)) {
            proceedFlag = true; // Success without an image
        } else if (imageResponse.startsWith("Failed to save the image: ")) {
            throw new FileFormatException(imageResponse);
        } else if ("Specified category not valid".equalsIgnoreCase(imageResponse)) {
            throw new RuntimeException(imageResponse);
        } else {
            employee.setPic(imageResponse);
            proceedFlag = true;
        }

        if (proceedFlag) {
            // Save the Employee; cascades save UserEntity if cascade type is set correctly
            return employeeRepository.save(employee);
        }

        return null;
    }

    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    public List<Employee> getAllActiveEmployees(Long school){
        return employeeRepository.findAllBySchool_IdAndStatusOrderByEmployeeNameAsc(school, "Active");
    }

    public int getAllActiveEmployeesCount(Long school){
        return employeeRepository.countAllBySchool_IdAndStatus(school, "Active");
    }

    public List<Employee> getAllActiveEmployees(){
        return employeeRepository.findAllByStatusOrderByEmployeeNameAsc("Active");
    }


    public UserEntity generateUsernameAndPassword(Employee employee, UserEntity userEntity) {
        log.info("Inside generateUsernameAndPassword");
        // Generate Username

        userEntity.setUsername(employee.getEmployeeCode());

        String password = generatePassword(employee.getEmployeeCode(), employee.getMobile1());
        userEntity.setPassword(passwordEncoder.encode(password));

        return userEntity;
    }

    // ── Employee List admin screen — "Reset Password" button ────────────────

    /** Generates an 8-char temp password an admin can hand to an employee — the
     *  admin can still overwrite it before saving, same as the Mobile Users flow. */
    public String generateTempPassword() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RNG.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Admin-driven password reset for an employee (Employee List "Reset Password"
     * button — ROLE_ADMIN/ROLE_SUPERADMIN only). Unlike the self-service
     * /auth/change-password flow, this doesn't require knowing the current
     * password, so the controller blocks an admin from using it on their own row
     * — self password changes must go through /auth/change-password instead.
     * Writes straight to the users table via the existing passwordEncoder bean,
     * same as every other password write in this codebase (BCrypt).
     */
    @Transactional
    public void adminResetPassword(Employee employee, String newPassword) {
        log.info("Inside adminResetPassword - employeeId={}", employee.getId());
        UserEntity userEntity = employee.getUserEntity();
        userEntity.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(userEntity);
    }

    public static String generatePassword(String employeeCode, String mobileNumber) {
        log.info("Inside generatePassword");
        String lastSixDigitsOfEmployeeCode = employeeCode.length() >= 6
                ? employeeCode.substring(employeeCode.length() - 6)
                : employeeCode;

        String lastFourDigitsOfMobileNumber = mobileNumber.length() >= 4
                ? mobileNumber.substring(mobileNumber.length() - 4)
                : mobileNumber;

        return lastSixDigitsOfEmployeeCode + lastFourDigitsOfMobileNumber;
    }

    public Optional<Employee> getEmployeeByUUID(UUID uuid){
        return employeeRepository.findByUuidAndStatus(uuid, "Active");
    }

    public List<String> getExistingRoleNames(Long employeeId) {
        log.info("Inside getExistingRoleNames");
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) return new ArrayList<>();
        List<String> roleNames = new ArrayList<>();
        for (Roles role : employee.getUserEntity().getRoles()) {
            String name = role.getName();
            // Map to friendly labels
            String label;
            switch (name) {
                case "ROLE_SUPERADMIN": label = "Super Admin (Developer)"; break;
                case "ROLE_ADMIN":      label = "Super Admin (School)"; break;
                case "ROLE_STAFF":      label = "Admin"; break;
                case "ROLE_TEACHER":    label = "Teacher"; break;
                case "ROLE_ACCOUNTENT": label = "Accountant"; break;
                case "ROLE_STUDENT":    label = "Student"; break;
                default: label = name.replace("ROLE_", ""); break;
            }
            roleNames.add(label);
        }
        return roleNames;
    }

    public boolean saveRoleUserMapping(Long userId, Long roleId){
        log.info("Inside saveRoleUserMapping");
        try{
            Employee employee = employeeRepository.findById(userId).orElse(null);
            Roles roles = roleRepository.findById(roleId).orElse(null);
            if(employee!=null && roles!=null){
                UserEntity user = employee.getUserEntity();
                if(!user.getRoles().contains(roles)){
                    user.getRoles().add(roles);
                    userService.saveUser(user);
                    return true;
                } else{
                    return false;
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Friendly display label for a role name — matches the badge labels already
     * used in user-role.html's Roles column, so the Manage Roles modal shows the
     * exact same text as the badge the user is revoking.
     */
    private String roleDisplayLabel(String roleName) {
        if (roleName == null) return "";
        switch (roleName) {
            case "ROLE_SUPERADMIN":
            case "ROLE_ADMIN":      return "Super Admin";
            case "ROLE_STAFF":      return "Admin";
            case "ROLE_TEACHER":    return "Teacher";
            case "ROLE_ACCOUNTENT": return "Accountant";
            case "ROLE_STUDENT":    return "Student";
            default:                return roleName.replace("ROLE_", "");
        }
    }

    /**
     * Roles currently assigned to an employee, with role IDs — used to populate
     * the Manage Roles / Revoke modal (the older getExistingRoleNames only
     * returns display strings, no ID, so it can't be used to build a revoke call).
     * Each entry: {"roleId": Long, "roleName": String (display label)}.
     */
    public List<Map<String, Object>> getExistingRolesDetailed(Long employeeId) {
        log.info("Inside getExistingRolesDetailed");
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Roles role : employee.getUserEntity().getRoles()) {
            Map<String, Object> row = new HashMap<>();
            row.put("roleId", role.getId());
            row.put("roleName", roleDisplayLabel(role.getName()));
            result.add(row);
        }
        return result;
    }

    /**
     * The target UserEntity id for a given employee — used by the controller's
     * self-lockout guard (checked BEFORE calling removeRoleFromUser) so it can
     * compare against the logged-in user without this service needing to know
     * anything about "who is calling".
     */
    public Long getUserIdForEmployee(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        return employee != null ? employee.getUserEntity().getId() : null;
    }

    /**
     * Removes a role from an employee's user account.
     * Returns false if the employee/role don't exist or the role isn't currently
     * assigned. Callers must apply any authorization/self-lockout checks (see
     * GlobalController's revoke endpoint) BEFORE calling this — this method just
     * performs the removal.
     */
    public boolean removeRoleFromUser(Long employeeId, Long roleId) {
        log.info("Inside removeRoleFromUser - employeeId={}, roleId={}", employeeId, roleId);
        try {
            Employee employee = employeeRepository.findById(employeeId).orElse(null);
            Roles role = roleRepository.findById(roleId).orElse(null);
            if (employee == null || role == null) {
                return false;
            }
            UserEntity user = employee.getUserEntity();
            if (!user.getRoles().contains(role)) {
                return false;
            }
            user.getRoles().remove(role);
            userService.saveUser(user);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public School getLoggedInEmployeeSchool(){
        log.info("Inside getLoggedInEmployeeSchool");
        Employee employee = employeeRepository.findByUserEntity(userService.getLoggedInUser());
        return  employee.getSchool();
    }

    public Optional<School> getLoggedInEmployeeSchool(String username) {
        return employeeRepository.findSchoolByUsername(username);
    }

    public List<String[]> getComingBirthDays(Long school, Long academic){
        log.info("Inside getComingBirthDays");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy");
        List<String[]> dataList = new ArrayList<>();
        try{
            // "Today" computed explicitly in IST and passed to the query — see javadoc on
            // EmployeeRepository.findTodaysBirthdays() for why (CURDATE() was returning
            // tomorrow's date on both local and server deployments).
            String todayMonthDay = LocalDate.now(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("MM-dd"));
            List<Object[]> stuDobList = employeeRepository.findTodaysBirthdays(school, "Active", todayMonthDay);
            if(!stuDobList.isEmpty()){
                for(Object[] dd:stuDobList){
                    // dob is now a plain SQL DATE column, returned as java.sql.Date — no
                    // timezone conversion involved (java.sql.Date doesn't even support
                    // toInstant()), so this is a direct, unambiguous conversion.
                    LocalDate dob = ((java.sql.Date) dd[0]).toLocalDate();
                    String formattedDob = dob.format(formatter);
                    String studentName = (String) dd[1];
                    String[] dobList = new String[4];
                    dobList[0] = formattedDob;
                    dobList[1] = studentName + " (" + dd[2] + ")";
                    dobList[2] = null; // no grade for employees
                    dobList[3] = null; // no section for employees
                    dataList.add(dobList);
                }
            }
            //Employee Data added
            return dataList;
        }catch(Exception e){
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
}