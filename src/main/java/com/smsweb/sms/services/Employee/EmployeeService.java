package com.smsweb.sms.services.Employee;

import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.helper.FileHandleHelper;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.Roles;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import com.smsweb.sms.repositories.users.RoleRepository;
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

@Service
public class EmployeeService {

    private final FileHandleHelper fileHandleHelper;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final RoleRepository roleRepository;

    public EmployeeService(FileHandleHelper fileHandleHelper, EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder, UserService userService, RoleRepository roleRepository) {
        this.fileHandleHelper = fileHandleHelper;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public Employee saveEmployee(Employee employee, MultipartFile logo, String fileNameOrSchoolCode, Employee existingEmployee) throws IOException {
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
            employee.setUserEntity(userEntity);
            // Ensure other necessary fields from UserEntity are retained
        } else {
            // Generate employee code for new employee
            employee.setEmployeeCode("ERN-" + fileNameOrSchoolCode);

            // Create new UserEntity for new employee
            userEntity = new UserEntity();

            // Generate username and password
            userEntity = generateUsernameAndPassword(employee, userEntity);
            userEntity.setEmail(employee.getUserEntity().getEmail());
            // Assign UserEntity to the new employee
            employee.setUserEntity(userEntity);
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
        // Generate Username

        userEntity.setUsername(employee.getEmployeeCode());

        String password = generatePassword(employee.getEmployeeCode(), employee.getMobile1());
        userEntity.setPassword(passwordEncoder.encode(password));

        return userEntity;
    }

    public static String generatePassword(String employeeCode, String mobileNumber) {
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

    public boolean saveRoleUserMapping(Long userId, Long roleId){
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
        Employee employee = employeeRepository.findByUserEntity(userService.getLoggedInUser());
        return  employee.getSchool();
    }

    public Optional<School> getLoggedInEmployeeSchool(String username) {
        return employeeRepository.findSchoolByUsername(username);
    }

    public List<String[]> getComingBirthDays(Long school, Long academic){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy");
        List<String[]> dataList = new ArrayList<>();
        try{
            List<Object[]> stuDobList = employeeRepository.findUpcomingBirthdaysInNext7Days(school, "Active");
            if(!stuDobList.isEmpty()){
                for(Object[] dd:stuDobList){
                    LocalDate dob = ((Date) dd[0]).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    String formattedDob = dob.format(formatter);
                    String studentName = (String) dd[1];
                    System.out.println("DOB: "+dd[0]+" Name: "+dd[1]);
                    String[] dobList = new String[2];
                    dobList[1] = studentName + " ("+dd[2]+")";
                    dobList[0] = formattedDob;
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