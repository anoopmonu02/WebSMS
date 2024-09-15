package com.smsweb.sms.services.Employee;

import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.helper.FileHandleHelper;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    private final UserRepository userRepository;

    public EmployeeService(FileHandleHelper fileHandleHelper, EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder, UserRepository userRepository) {
        this.fileHandleHelper = fileHandleHelper;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
    }

    @Transactional
    public Employee saveEmployee(Employee employee, MultipartFile logo, String fileNameOrSchoolCode, Employee existingEmployee) throws IOException {
        String imageResponse = fileHandleHelper.saveImage("employee", logo);
        boolean proceedFlag = false;

        // Handle the image upload response
        if (imageResponse == null || imageResponse.isEmpty()) {
            // Use existing pic and other fields if employee already exists
            if (existingEmployee != null) {
                employee.setPic(existingEmployee.getPic());
                employee.setEmployeeCode(existingEmployee.getEmployeeCode());
                employee.setUsername(existingEmployee.getUsername());
                employee.setPassword(existingEmployee.getPassword());
            }
            proceedFlag = true;
        } else if ("Success_no_image".equalsIgnoreCase(imageResponse)) {
            proceedFlag = true;
            if (existingEmployee != null) {
                employee.setPic(existingEmployee.getPic());
                employee.setEmployeeCode(existingEmployee.getEmployeeCode());
                employee.setUsername(existingEmployee.getUsername());
                employee.setPassword(existingEmployee.getPassword());
            }
        } else if (imageResponse.startsWith("Failed to save the image: ")) {
            throw new FileFormatException(imageResponse);
        } else if ("Specified category not valid".equalsIgnoreCase(imageResponse)) {
            throw new RuntimeException(imageResponse);
        } else {
            // New image is set
            employee.setPic(imageResponse);
            employee.setEmployeeCode("ERN-" + fileNameOrSchoolCode);
            proceedFlag = true;
        }

        // Ensure unique fields are not duplicated
        if (proceedFlag) {
            // For new employee: Generate username and password if not updating
            if (existingEmployee == null) {
                employee.setPic(imageResponse);
                employee.setEmployeeCode("ERN-" + fileNameOrSchoolCode);
                employee = generateUsernameAndPassword(employee);
            } else {
                // Ensure that username and email are unique and not null/empty
                if (employee.getUsername() == null || employee.getUsername().isEmpty()) {
                    employee.setUsername(existingEmployee.getUsername());
                }
                if (employee.getEmail() == null || employee.getEmail().isEmpty()) {
                    employee.setEmail(existingEmployee.getEmail());
                }
            }
        }

        // Set createdBy or updatedBy fields
        if (existingEmployee != null) {
            employee.setUpdatedBy(getLoggedInUser());
        } else {
            employee.setCreatedBy(getLoggedInUser());
        }

        // Check for duplicate email or username before saving
        Optional<Employee> employeeWithSameUsername = employeeRepository.findByUsername(employee.getUsername());
        Optional<Employee> employeeWithSameEmail = employeeRepository.findByEmail(employee.getEmail());

        if (employeeWithSameUsername.isPresent() && !employeeWithSameUsername.get().getUuid().equals(employee.getUuid())) {
            throw new UniqueConstraintsException("Username already exists");
        }

        if (employeeWithSameEmail.isPresent() && !employeeWithSameEmail.get().getUuid().equals(employee.getUuid())) {
            throw new UniqueConstraintsException("Email already exists");
        }

        // Save the employee entity
        return employeeRepository.save(employee);
    }

    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    public List<Employee> getAllActiveEmployees(Long school){
        return employeeRepository.findAllBySchool_IdAndStatusOrderByEmployeeNameAsc(school, "Active");
    }


    public Employee generateUsernameAndPassword(Employee employee) {
        // Generate Username

        employee.setUsername(employee.getEmployeeCode());

        String password = generatePassword(employee.getEmployeeCode(), employee.getMobile1());
        employee.setPassword(passwordEncoder.encode(password));

        return employee;
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

    private UserEntity getLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userRepository.findByUsername(userDetails.getUsername());
        }
        return null;
    }
}