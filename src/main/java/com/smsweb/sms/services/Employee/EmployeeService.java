package com.smsweb.sms.services.Employee;

import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.helper.FileHandleHelper;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
    public Employee saveEmployee(Employee employee, MultipartFile logo, String fileNameOrSchoolCode) throws IOException {
        String imageResponse = fileHandleHelper.saveImage("employee", logo);
        try{
            System.out.println("Image: "+imageResponse);
            boolean proceedFlag = false;
            boolean foundImageResponse = (imageResponse!=null && imageResponse!="")?true:false;
            if(foundImageResponse && imageResponse.equalsIgnoreCase("Success_no_image")){
                proceedFlag = true;
            } else if(foundImageResponse && imageResponse.equalsIgnoreCase("Either image format not supported or size exceeded 2MB.")){
                throw new FileSizeLimitExceededException("Either image format not supported or size exceeded 2MB.");
            } else if (foundImageResponse && imageResponse.startsWith("Failed to save the image: ")) {
                throw new FileFormatException(imageResponse);
            } else if (foundImageResponse && imageResponse.equalsIgnoreCase("Specified category not valid")) {
                throw new RuntimeException(imageResponse);
            } else{
                employee.setPic(imageResponse);
                employee.setEmployeeCode("ERN-"+fileNameOrSchoolCode);
                proceedFlag = true;
            }

            if(proceedFlag){
                //write employee code here
                employee = generateUsernameAndPassword(employee);
            }

            //employee = generateUsernameAndPassword(employee);
            return employeeRepository.save(employee);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ObjectNotSaveException("",e);
        }
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

        // Generate Password
        String password = generatePassword(employee.getEmployeeCode(), employee.getMobile1());
        employee.setPassword(passwordEncoder.encode(password));

        // Optionally, you can send the password to the employee's email
        // emailService.sendEmail(employee.getEmail(), "Your account details", "Username: " + username + "\nPassword: " + password);

        return employee;
    }



    public static String generatePassword(String employeeCode, String mobileNumber) {
        // Extract last 6 digits of employee code
        String lastSixDigitsOfEmployeeCode = employeeCode.length() >= 6
                ? employeeCode.substring(employeeCode.length() - 6)
                : employeeCode;

        // Extract last 4 digits of mobile number
        String lastFourDigitsOfMobileNumber = mobileNumber.length() >= 4
                ? mobileNumber.substring(mobileNumber.length() - 4)
                : mobileNumber;

        // Concatenate the two parts to form the password
        return lastSixDigitsOfEmployeeCode + lastFourDigitsOfMobileNumber;
    }

    public Optional<Employee> getEmployeeByUUID(UUID uuid){
        return employeeRepository.findByUuidAndStatus(uuid, "Active");
    }
}