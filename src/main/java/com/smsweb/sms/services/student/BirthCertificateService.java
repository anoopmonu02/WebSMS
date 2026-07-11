package com.smsweb.sms.services.student;

import com.smsweb.sms.dto.BirthCertificateStudentDto;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.student.StudentRegionalDetail;
import com.smsweb.sms.repositories.student.StudentRegionalDetailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reports > Student Report > Birth Certificate.
 *
 * Read-only screen: look up a single active student (by UUID) and hand back
 * every field the printable, bilingual (English + regional-language)
 * certificate needs. Nothing is ever saved here.
 */
@Service
public class BirthCertificateService {

    private static final Logger log = LoggerFactory.getLogger(BirthCertificateService.class);
    private static final SimpleDateFormat DOB_FORMAT = new SimpleDateFormat("dd MMM, yyyy");

    private final AcademicStudentService academicStudentService;
    private final StudentRegionalDetailRepository regionalDetailRepository;

    public BirthCertificateService(AcademicStudentService academicStudentService,
                                    StudentRegionalDetailRepository regionalDetailRepository) {
        this.academicStudentService = academicStudentService;
        this.regionalDetailRepository = regionalDetailRepository;
    }

    /**
     * Live search — same behaviour (name / father name / mother name / SR no,
     * 10-per-page, current school + academic year only) as every other
     * search-as-you-type screen in the app.
     */
    public List<BirthCertificateStudentDto> searchStudents(String query, Long academicYearId, Long schoolId, int page) {
        log.info("Inside searchStudents — query={}, page={}", query, page);
        List<AcademicStudent> matches = academicStudentService.searchStudents(query, academicYearId, schoolId, page);
        List<BirthCertificateStudentDto> results = new ArrayList<>();
        if (matches == null) return results;

        List<Long> studentIds = matches.stream()
                .map(as -> as.getStudent().getId())
                .collect(Collectors.toList());
        Map<Long, StudentRegionalDetail> existingByStudentId = regionalDetailRepository
                .findAllByStudent_IdIn(studentIds).stream()
                .collect(Collectors.toMap(rd -> rd.getStudent().getId(), rd -> rd));

        for (AcademicStudent as : matches) {
            results.add(toDto(as, existingByStudentId.get(as.getStudent().getId())));
        }
        return results;
    }

    private BirthCertificateStudentDto toDto(AcademicStudent as, StudentRegionalDetail regional) {
        Student student = as.getStudent();
        BirthCertificateStudentDto dto = new BirthCertificateStudentDto();
        dto.setStudentUuid(student.getUuid() != null ? student.getUuid().toString() : null);
        dto.setStudentName(student.getStudentName());
        dto.setFatherName(student.getFatherName());
        dto.setMotherName(student.getMotherName());
        dto.setAddress(buildFullAddress(student));
        dto.setGender(student.getGender());
        dto.setNationality(student.getNationality());
        dto.setReligion(student.getReligion());
        dto.setRegistrationNo(student.getRegistrationNo());
        dto.setDob(student.getDob() != null ? DOB_FORMAT.format(student.getDob()) : null);
        dto.setClassSrNo(as.getClassSrNo());
        dto.setGradeName(as.getGrade() != null ? as.getGrade().getGradeName() : null);
        dto.setSectionName(as.getSection() != null ? as.getSection().getSectionName() : null);

        if (regional != null) {
            dto.setStudentNameRegional(regional.getStudentNameRegional());
            dto.setFatherNameRegional(regional.getFatherNameRegional());
            dto.setMotherNameRegional(regional.getMotherNameRegional());
            dto.setAddressRegional(regional.getAddressRegional());
        }
        return dto;
    }

    /**
     * Full address (street address + city, in caps to match the rest of the address)
     * shown on the certificate. Skips appending the city if that name is already
     * present somewhere in the free-text address (common when the address was
     * originally typed as "village, district") to avoid showing the same place name twice.
     */
    private String buildFullAddress(Student student) {
        if (student == null) return "";
        String rawAddress = student.getAddress() != null ? student.getAddress().trim() : "";
        String addressLower = rawAddress.toLowerCase();
        StringBuilder sb = new StringBuilder(rawAddress);

        if (student.getCity() != null && student.getCity().getCityName() != null && !student.getCity().getCityName().isBlank()) {
            String cityName = student.getCity().getCityName().trim();
            if (!addressLower.contains(cityName.toLowerCase())) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(cityName.toUpperCase());
            }
        }
        return sb.toString();
    }
}
