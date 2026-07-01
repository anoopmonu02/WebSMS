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
            List<Object[]> stuDobList = employeeRepository.findTodaysBirthdays(school, "Active");
            if(!stuDobList.isEmpty()){
                for(Object[] dd:stuDobList){
                    LocalDate dob = ((Date) dd[0]).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
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