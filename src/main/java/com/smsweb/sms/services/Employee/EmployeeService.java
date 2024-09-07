package com.smsweb.sms.services.Employee;

import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.helper.FileHandleHelper;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import org.springframework.dao.DataIntegrityViolationException;
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

    public EmployeeService(FileHandleHelper fileHandleHelper, EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder) {
        this.fileHandleHelper = fileHandleHelper;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Employee saveEmployee(Employee employee, MultipartFile logo, String fileNameOrSchoolCode, Employee existingEmployee) throws IOException {
        String imageResponse = fileHandleHelper.saveImage("employee", logo);
        boolean proceedFlag = false;

        if (imageResponse == null || imageResponse.isEmpty()) {
            if (existingEmployee != null) {
                if (existingEmployee.getPic() != null && !existingEmployee.getPic().isEmpty()) {
                    employee.setPic(existingEmployee.getPic());
                }
                employee.setEmployeeCode(existingEmployee.getEmployeeCode());
                employee.setUsername(existingEmployee.getUsername());
                employee.setPassword(existingEmployee.getPassword());
            }
            proceedFlag = true;
        } else if ("Success_no_image".equalsIgnoreCase(imageResponse)) {
            proceedFlag = true;
            if (existingEmployee != null) {
                if (existingEmployee.getPic() != null && !existingEmployee.getPic().isEmpty()) {
                    employee.setPic(existingEmployee.getPic());
                }
                employee.setEmployeeCode(existingEmployee.getEmployeeCode());
                employee.setUsername(existingEmployee.getUsername());
                employee.setPassword(existingEmployee.getPassword());
            }
        } else if (imageResponse.startsWith("Failed to save the image: ")) {
            throw new FileFormatException(imageResponse);
        } else if ("Specified category not valid".equalsIgnoreCase(imageResponse)) {
            throw new RuntimeException(imageResponse);
        } else {
            employee.setPic(imageResponse);
            employee.setEmployeeCode("ERN-" + fileNameOrSchoolCode);
            proceedFlag = true;
        }

        if (proceedFlag) {
            if (existingEmployee == null) {
                employee = generateUsernameAndPassword(employee);
            }
        }

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
}