package com.smsweb.sms.services.admin;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AcademicyearService {
    private final AcademicyearRepository academicyearRepository;
    private final UserRepository userRepository;

    @Autowired
    public AcademicyearService(AcademicyearRepository academicyearRepository, UserRepository userRepository){
        this.academicyearRepository = academicyearRepository;
        this.userRepository = userRepository;
    }

    public List<AcademicYear> getAllAcademiyears(Long schoolid){
        //return academicyearRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
        return academicyearRepository.findAllBySchoolIdOrderByIdDesc(schoolid);
    }

    public Optional<AcademicYear> getAcademicyearById(Long id){
        return academicyearRepository.findById(id);
    }

    @Transactional
    public AcademicYear save(AcademicYear academicYear){
        academicyearRepository.save(academicYear);
        return academicYear;
    }

    public AcademicYear getCurrentAcademicYear(){
        return academicyearRepository.findTopByStatusOrderByIdDesc("active");
    }

    public UserEntity getLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userRepository.findByUsername(userDetails.getUsername());
        }
        return null;
    }

}
