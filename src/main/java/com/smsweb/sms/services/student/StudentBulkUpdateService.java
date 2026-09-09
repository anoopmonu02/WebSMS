package com.smsweb.sms.services.student;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.mobile.StudentHealthInfo;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.universal.Bank;
import com.smsweb.sms.models.universal.Cast;
import com.smsweb.sms.models.universal.Category;
import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.models.universal.Province;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import com.smsweb.sms.repositories.universal.BankRepository;
import com.smsweb.sms.repositories.universal.CastRepository;
import com.smsweb.sms.repositories.universal.CategoryRepository;
import com.smsweb.sms.repositories.universal.CityRepository;
import com.smsweb.sms.repositories.universal.ProvinceRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import com.smsweb.sms.services.mobile.StudentHealthInfoService;
import com.smsweb.sms.services.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * NEW, isolated service backing the "Update Student Details (Group-wise)"
 * page. Does not modify StudentService, AcademicStudentService or any other
 * existing service/repository — it only reads/writes Student, AcademicStudent
 * (classSrNo only), StudentHealthInfo (via the existing StudentHealthInfoService
 * upsert methods) and UserEntity.email, using the exact same repositories those
 * classes already use elsewhere.
 */
@Service
public class StudentBulkUpdateService {

    private static final Logger log = LoggerFactory.getLogger(StudentBulkUpdateService.class);

    @Autowired
    private AcademicStudentRepository academicStudentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private StudentHealthInfoService studentHealthInfoService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private CastRepository castRepository;
    @Autowired
    private BankRepository bankRepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private ProvinceRepository provinceRepository;
    @Autowired
    private UserService userService;

    private static final Set<String> GENDER_VALUES = Set.of("MALE", "FEMALE", "NO_PREFERENCE");
    private static final Pattern DIGITS_ONLY = Pattern.compile("^[0-9]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public List<Map<String, Object>> getRowsForGroup(Long mediumId, Long gradeId, Long sectionId, String groupKey,
                                                       Long academicYearId, Long schoolId) {
        log.info("Inside getRowsForGroup, groupKey={}", groupKey);
        StudentFieldGroupRegistry.GroupDef group = StudentFieldGroupRegistry.getByKey(groupKey);
        if (group == null) {
            throw new IllegalArgumentException("Unknown data selection.");
        }
        List<AcademicStudent> list = academicStudentRepository
                .findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatusIgnoreCase(
                        schoolId, mediumId, gradeId, sectionId, academicYearId, "Active");

        List<Map<String, Object>> rows = new ArrayList<>();
        int sno = 1;
        for (AcademicStudent as : list) {
            Student s = as.getStudent();
            if (s == null || as.getUuid() == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sno", sno++);
            row.put("uuid", as.getUuid().toString());
            row.put("studentName", nvl(s.getStudentName()));
            row.put("fatherName", nvl(s.getFatherName()));
            row.put("motherName", nvl(s.getMotherName()));
            row.put("psrn", s.getPsrn() != null ? s.getPsrn() : "");
            row.put("values", buildValuesForGroup(groupKey, as, s));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> buildValuesForGroup(String groupKey, AcademicStudent as, Student s) {
        Map<String, Object> values = new LinkedHashMap<>();
        switch (groupKey) {
            case "SR_NO":
                values.put("classSrNo", nvl(as.getClassSrNo()));
                break;
            case "GENDER_QUALIFICATION":
                values.put("gender", nvl(s.getGender()));
                values.put("fatherQualification", nvl(s.getFatherQualification()));
                values.put("motherQualification", nvl(s.getMotherQualification()));
                break;
            case "RELIGION_CATEGORY_CASTE":
                values.put("religion", nvl(s.getReligion()));
                values.put("category", s.getCategory() != null
                        ? lookupIdName(s.getCategory().getId(), s.getCategory().getCategoryName()) : null);
                values.put("cast", s.getCast() != null
                        ? lookupIdName(s.getCast().getId(), s.getCast().getCastName()) : null);
                break;
            case "ADDRESS_CITY_PINCODE":
                values.put("address", nvl(s.getAddress()));
                values.put("province", s.getProvince() != null
                        ? lookupIdName(s.getProvince().getId(), s.getProvince().getProvinceName()) : null);
                values.put("city", s.getCity() != null
                        ? lookupIdName(s.getCity().getId(), s.getCity().getCityName()) : null);
                values.put("pincode", nvl(s.getPincode()));
                break;
            case "BANK_BRANCH_IFSC_ACCOUNT":
                values.put("bank", s.getBank() != null
                        ? lookupIdName(s.getBank().getId(), s.getBank().getBankName()) : null);
                values.put("branchName", nvl(s.getBranchName()));
                values.put("ifscCode", nvl(s.getIfscCode()));
                values.put("accountNo", nvl(s.getAccountNo()));
                break;
            case "HEIGHT_WEIGHT": {
                Optional<StudentHealthInfo> hi = studentHealthInfoService.getByAcademicStudentId(as.getId());
                values.put("height", hi.map(StudentHealthInfo::getHeight).orElse(null));
                values.put("weight", hi.map(StudentHealthInfo::getWeight).orElse(null));
                break;
            }
            case "HEALTH_EYE_ISSUE": {
                Optional<StudentHealthInfo> hi = studentHealthInfoService.getByAcademicStudentId(as.getId());
                values.put("haveHealthIssues", hi.map(StudentHealthInfo::getHaveHealthIssues).orElse(false));
                values.put("haveEyeIssue", hi.map(StudentHealthInfo::getHaveEyeIssue).orElse(false));
                break;
            }
            case "MOBILE1_MOBILE2":
                values.put("mobile1", nvl(s.getMobile1()));
                values.put("mobile2", nvl(s.getMobile2()));
                break;
            case "DOB":
                values.put("dob", s.getDob() != null ? s.getDob().toString() : "");
                break;
            case "FATHER_OCCUPATION":
                values.put("fatherOccupation", nvl(s.getFatherOccupation()));
                break;
            case "EMAIL":
                values.put("email", s.getUserEntity() != null ? nvl(s.getUserEntity().getEmail()) : "");
                break;
            case "DISTANCE_FROM_SCHOOL":
                values.put("distanceFromSchool", s.getDistanceFromSchool());
                break;
            case "AADHAR_NO":
                values.put("aadharNo", nvl(s.getAadharNo()));
                break;
            case "PEN_NO":
                values.put("penNo", nvl(s.getPenNo()));
                break;
            case "APAAR_ID":
                values.put("apaarId", nvl(s.getApaarId()));
                break;
            case "BLOOD_GROUP":
                values.put("bloodGroup", nvl(s.getBloodGroup()));
                break;
            case "BODY_TYPE":
                values.put("bodyType", nvl(s.getBodyType()));
                break;
            default:
                break;
        }
        return values;
    }

    @Transactional
    public List<Map<String, Object>> saveFieldGroup(String groupKey, List<Map<String, Object>> rows,
                                                      Long academicYearId, Long schoolId) {
        log.info("Inside saveFieldGroup, groupKey={}, rowCount={}", groupKey, rows != null ? rows.size() : 0);
        List<Map<String, Object>> results = new ArrayList<>();
        StudentFieldGroupRegistry.GroupDef group = StudentFieldGroupRegistry.getByKey(groupKey);
        if (group == null) {
            results.add(rowResult("", false, "Unknown data selection."));
            return results;
        }
        UserEntity actor = userService.getLoggedInUser();
        for (Map<String, Object> row : rows) {
            String uuid = row != null ? String.valueOf(row.get("uuid")) : "";
            @SuppressWarnings("unchecked")
            Map<String, Object> values = row != null && row.get("values") instanceof Map
                    ? (Map<String, Object>) row.get("values") : Map.of();
            try {
                if (uuid == null || uuid.isBlank() || "null".equals(uuid)) {
                    results.add(rowResult(uuid, false, "Missing student reference."));
                    continue;
                }
                AcademicStudent as = academicStudentRepository.findByUuid(UUID.fromString(uuid)).orElse(null);
                if (as == null || as.getStudent() == null
                        || as.getSchool() == null || !as.getSchool().getId().equals(schoolId)
                        || as.getAcademicYear() == null || !as.getAcademicYear().getId().equals(academicYearId)) {
                    results.add(rowResult(uuid, false, "Student not found for the current school/academic year."));
                    continue;
                }
                String error = validateAndApply(groupKey, as, values, actor);
                results.add(rowResult(uuid, error == null, error == null ? "Saved" : error));
            } catch (Exception e) {
                log.error("Error saving row for group {}", groupKey, e);
                results.add(rowResult(uuid, false, "Unexpected error: " + e.getMessage()));
            }
        }
        return results;
    }

    private String validateAndApply(String groupKey, AcademicStudent as, Map<String, Object> values, UserEntity actor) {
        Student s = as.getStudent();
        switch (groupKey) {
            case "SR_NO": {
                as.setClassSrNo(str(values.get("classSrNo")));
                academicStudentRepository.save(as);
                return null;
            }
            case "GENDER_QUALIFICATION": {
                String gender = str(values.get("gender"));
                if (!GENDER_VALUES.contains(gender)) return "Please select a valid Gender.";
                s.setGender(gender);
                s.setFatherQualification(str(values.get("fatherQualification")));
                s.setMotherQualification(str(values.get("motherQualification")));
                studentRepository.save(s);
                return null;
            }
            case "RELIGION_CATEGORY_CASTE": {
                Long categoryId = idFromLookup(values.get("category"));
                Long castId = idFromLookup(values.get("cast"));
                if (categoryId == null) return "Please select a Category.";
                if (castId == null) return "Please select a Caste.";
                Category category = categoryRepository.findById(categoryId).orElse(null);
                Cast cast = castRepository.findById(castId).orElse(null);
                if (category == null) return "Selected Category no longer exists.";
                if (cast == null) return "Selected Caste no longer exists.";
                s.setReligion(str(values.get("religion")));
                s.setCategory(category);
                s.setCast(cast);
                studentRepository.save(s);
                return null;
            }
            case "ADDRESS_CITY_PINCODE": {
                String address = str(values.get("address"));
                String pincode = str(values.get("pincode"));
                if (address.isBlank()) return "Address is required.";
                if (!pincode.isBlank() && !pincode.matches("^[0-9]{6}$")) return "Pincode must be a 6-digit number.";
                Long provinceId = idFromLookup(values.get("province"));
                Long cityId = idFromLookup(values.get("city"));
                if (provinceId == null) return "Please select a Province.";
                if (cityId == null) return "Please select a City.";
                Province province = provinceRepository.findById(provinceId).orElse(null);
                City city = cityRepository.findById(cityId).orElse(null);
                if (province == null) return "Selected Province no longer exists.";
                if (city == null) return "Selected City no longer exists.";
                s.setAddress(address);
                s.setProvince(province);
                s.setCity(city);
                s.setPincode(pincode);
                studentRepository.save(s);
                return null;
            }
            case "BANK_BRANCH_IFSC_ACCOUNT": {
                Long bankId = idFromLookup(values.get("bank"));
                if (bankId == null) return "Please select a Bank.";
                Bank bank = bankRepository.findById(bankId).orElse(null);
                if (bank == null) return "Selected Bank no longer exists.";
                String accountNo = str(values.get("accountNo"));
                if (!accountNo.isBlank() && !DIGITS_ONLY.matcher(accountNo).matches()) {
                    return "Account number must contain digits only.";
                }
                s.setBank(bank);
                s.setBranchName(str(values.get("branchName")));
                s.setIfscCode(str(values.get("ifscCode")));
                s.setAccountNo(accountNo);
                studentRepository.save(s);
                return null;
            }
            case "HEIGHT_WEIGHT": {
                Integer height = intOrNull(values.get("height"));
                Integer weight = intOrNull(values.get("weight"));
                if (height != null && (height < 0 || height > 999)) return "Height must be a valid number up to 3 digits.";
                if (weight != null && (weight < 0 || weight > 999)) return "Weight must be a valid number up to 3 digits.";
                StudentHealthInfo existing = studentHealthInfoService.getByAcademicStudentId(as.getId()).orElse(null);
                boolean haveHealth = existing != null && Boolean.TRUE.equals(existing.getHaveHealthIssues());
                boolean haveEye = existing != null && Boolean.TRUE.equals(existing.getHaveEyeIssue());
                String desc = existing != null ? existing.getHealthIssueDescription() : null;
                studentHealthInfoService.updateForStudent(as, height, weight, haveHealth, haveEye, desc, actor);
                return null;
            }
            case "HEALTH_EYE_ISSUE": {
                boolean haveHealth = boolVal(values.get("haveHealthIssues"));
                boolean haveEye = boolVal(values.get("haveEyeIssue"));
                StudentHealthInfo existing = studentHealthInfoService.getByAcademicStudentId(as.getId()).orElse(null);
                Integer height = existing != null ? existing.getHeight() : null;
                Integer weight = existing != null ? existing.getWeight() : null;
                String desc = existing != null ? existing.getHealthIssueDescription() : null;
                studentHealthInfoService.updateForStudent(as, height, weight, haveHealth, haveEye, desc, actor);
                return null;
            }
            case "MOBILE1_MOBILE2": {
                String m1 = str(values.get("mobile1"));
                String m2 = str(values.get("mobile2"));
                if (!m1.isBlank() && !m1.matches("^[0-9]{10}$")) return "Mobile1 must be exactly 10 digits.";
                if (!m2.isBlank() && !m2.matches("^[0-9]{10}$")) return "Mobile2 must be exactly 10 digits.";
                s.setMobile1(m1);
                s.setMobile2(m2);
                studentRepository.save(s);
                return null;
            }
            case "DOB": {
                String v = str(values.get("dob"));
                if (v.isBlank()) return "DOB is required.";
                LocalDate dob;
                try {
                    dob = LocalDate.parse(v);
                } catch (Exception e) {
                    return "Please enter a valid date.";
                }
                if (dob.isAfter(LocalDate.now())) return "DOB cannot be in the future.";
                s.setDob(dob);
                studentRepository.save(s);
                return null;
            }
            case "FATHER_OCCUPATION": {
                s.setFatherOccupation(str(values.get("fatherOccupation")));
                studentRepository.save(s);
                return null;
            }
            case "EMAIL": {
                String email = str(values.get("email"));
                if (!EMAIL_PATTERN.matcher(email).matches()) return "Please enter a valid email address.";
                if (s.getUserEntity() == null) return "This student has no login account to update.";
                UserEntity existingByEmail = userRepository.findByEmail(email);
                if (existingByEmail != null && !existingByEmail.getId().equals(s.getUserEntity().getId())) {
                    return "This email is already used by another student.";
                }
                s.getUserEntity().setEmail(email);
                userRepository.save(s.getUserEntity());
                return null;
            }
            case "DISTANCE_FROM_SCHOOL": {
                Integer distance = intOrNull(values.get("distanceFromSchool"));
                if (distance != null && distance < 0) return "Distance must be a valid number.";
                s.setDistanceFromSchool(distance);
                studentRepository.save(s);
                return null;
            }
            case "AADHAR_NO": {
                String v = str(values.get("aadharNo"));
                if (!v.isBlank() && !v.matches("^[0-9]{12}$")) return "Aadhar number must be exactly 12 digits.";
                s.setAadharNo(v);
                studentRepository.save(s);
                return null;
            }
            case "PEN_NO": {
                String v = str(values.get("penNo"));
                if (!v.isBlank() && !v.matches("^[0-9]{11}$")) return "PEN number must be exactly 11 digits.";
                s.setPenNo(v);
                studentRepository.save(s);
                return null;
            }
            case "APAAR_ID": {
                String v = str(values.get("apaarId"));
                if (!v.isBlank() && !v.matches("^[0-9]{12}$")) return "Apaar Id must be exactly 12 digits.";
                s.setApaarId(v);
                studentRepository.save(s);
                return null;
            }
            case "BLOOD_GROUP": {
                s.setBloodGroup(str(values.get("bloodGroup")));
                studentRepository.save(s);
                return null;
            }
            case "BODY_TYPE": {
                s.setBodyType(str(values.get("bodyType")));
                studentRepository.save(s);
                return null;
            }
            default:
                return "Unsupported data selection.";
        }
    }

    /**
     * Async blur-time check backing the Email field's "already used by another student"
     * validation. excludeAcademicStudentUuid lets the currently-edited student keep their
     * own existing email without tripping the uniqueness check on themselves.
     */
    public boolean isEmailAvailable(String email, String excludeAcademicStudentUuid) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) return false;
        UserEntity existing = userRepository.findByEmail(email);
        if (existing == null) return true;
        if (excludeAcademicStudentUuid == null || excludeAcademicStudentUuid.isBlank()) return false;
        try {
            AcademicStudent as = academicStudentRepository.findByUuid(UUID.fromString(excludeAcademicStudentUuid)).orElse(null);
            if (as == null || as.getStudent() == null || as.getStudent().getUserEntity() == null) return false;
            return existing.getId().equals(as.getStudent().getUserEntity().getId());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Map<String, Object> rowResult(String uuid, boolean success, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", uuid);
        result.put("success", success);
        result.put("message", message);
        return result;
    }

    private Map<String, Object> lookupIdName(Long id, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name != null ? name : "");
        return m;
    }

    private Long idFromLookup(Object value) {
        if (value == null) return null;
        if (value instanceof Map) {
            return toLong(((Map<?, ?>) value).get("id"));
        }
        return toLong(value);
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        String s = value.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        String s = value.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean boolVal(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    private String str(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
