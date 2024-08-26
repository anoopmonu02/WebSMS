package com.smsweb.sms.services.users;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.users.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public void saveUser(UserEntity userEntity) throws DuplicateUserException {
        try {
            // Handle different types of UserEntity here if needed
            if (userEntity instanceof Employee) {
                saveEmployee((Employee) userEntity);
            } else if (userEntity instanceof Student) {
                saveStudent((Student) userEntity);
            } else {
                userRepository.save(userEntity);
            }
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateUserException("Username or email already exists");
        }
    }

    private void saveEmployee(Employee employee) {
        // Save logic specific to Employee if needed
        userRepository.save(employee);
    }

    private void saveStudent(Student student) {
        // Save logic specific to Student if needed
        userRepository.save(student);
    }



    public UserEntity findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public void updatePassword(UserEntity user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
