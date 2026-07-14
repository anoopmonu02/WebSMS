package com.smsweb.sms.services.admin;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class AcademicyearService {
    private static final Logger log = LoggerFactory.getLogger(AcademicyearService.class);

    private final AcademicyearRepository academicyearRepository;
    private final UserRepository userRepository;
    private SchoolService schoolService;

    @Autowired
    public AcademicyearService(AcademicyearRepository academicyearRepository, UserRepository userRepository, SchoolService schoolService){
        this.academicyearRepository = academicyearRepository;
        this.userRepository = userRepository;
        this.schoolService = schoolService;
    }

    public List<AcademicYear> getAllAcademiyears(Long schoolid){
        //return academicyearRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
        return academicyearRepository.findAllBySchoolIdOrderByIdDesc(schoolid);
    }

    public List<AcademicYear> getAllAcademicYear(){
        return academicyearRepository.findAll();
    }

    public Optional<AcademicYear> getAcademicyearById(Long id){
        return academicyearRepository.findById(id);
    }

    @Transactional
    public AcademicYear save(AcademicYear academicYear){
        log.info("Inside save");
        academicyearRepository.save(academicYear);
        return academicYear;
    }

    public AcademicYear getCurrentAcademicYear(){
        log.info("Inside getCurrentAcademicYear");
        AcademicYear ay = academicyearRepository.findTopByStatusOrderByIdDesc("active");
        if (ay == null) ay = academicyearRepository.findTopByStatusOrderByIdDesc("Active");
        return ay;
    }

    public AcademicYear getCurrentAcademicYear(Long schoolid){
        // Bounded to the single most recent match (findTopBy... = LIMIT 1) so this can never
        // throw NonUniqueResultException, even if a school ends up with more than one
        // AcademicYear row marked active. Checks both status casings, same as the no-arg overload above.
        AcademicYear ay = academicyearRepository.findTopByStatusAndSchool_IdOrderByIdDesc("active", schoolid);
        if (ay == null) ay = academicyearRepository.findTopByStatusAndSchool_IdOrderByIdDesc("Active", schoolid);
        return ay;
    }

    public String delete(Long id) {
        log.info("Inside delete");
        try {
            if (!academicyearRepository.existsById(id)) {
                return "Academic year not found: " + id;
            }
            academicyearRepository.deleteById(id);
            return "success";
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return "Cannot delete: this academic year has linked data (students, fees, mappings, etc.). Remove all related data first.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    public UserEntity getLoggedInUser() {
        log.info("Inside getLoggedInUser");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userRepository.findByUsername(userDetails.getUsername());
        }
        return null;
    }

    public AcademicYear saveAcademicYearIfNotFound() {
        log.info("Inside saveAcademicYearIfNotFound");
        try {
            List<School> schools = schoolService.getAllSchools();
            int year = LocalDate.now().getYear();
            LocalDate startDate = LocalDate.of(year, 4, 1);
            LocalDate endDate = LocalDate.of(year + 1, 3, 31);

            Date startDateConverted = convertToDate(startDate);
            Date endDateConverted = convertToDate(endDate);

            for (School school : schools) {
                List<AcademicYear> academicYears = getAllAcademiyears(school.getId());

                // Proceed only if no academic years exist
                if (academicYears.isEmpty()) {
                    log.info("No academic year found for school={}, creating new one", school.getSchoolName());
                    AcademicYear academicYear = createNewAcademicYear(school, startDateConverted, endDateConverted, year);
                    return academicyearRepository.save(academicYear);
                } else {
                    log.debug("Academic year already exists for school={}", school.getSchoolName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();  // Ideally, use a logger instead of printing stack trace
        }
        return null;
    }
    private Date convertToDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private AcademicYear createNewAcademicYear(School school, Date startDate, Date endDate, int year) {
        AcademicYear academicYear = new AcademicYear();
        academicYear.setSchool(school);
        academicYear.setStartDate(startDate);
        academicYear.setEndDate(endDate);
        academicYear.setSessionFormat(year + "-" + (year + 1));
        return academicYear;
    }

}
