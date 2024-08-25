package com.smsweb.sms.services.admin;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.repositories.users.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Employee saveEmployee(Employee employee) {
        employee = generateUsernameAndPassword(employee);
        return employeeRepository.save(employee);
    }

    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
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
}