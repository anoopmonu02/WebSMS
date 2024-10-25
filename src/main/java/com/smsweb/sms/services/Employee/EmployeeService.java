package com.smsweb.sms.services.Employee;

import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.helper.FileHandleHelper;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.Roles;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import com.smsweb.sms.repositories.users.RoleRepository;
import com.smsweb.sms.services.users.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}