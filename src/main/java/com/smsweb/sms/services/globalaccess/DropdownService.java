package com.smsweb.sms.services.globalaccess;

import com.smsweb.sms.models.universal.*;
import com.smsweb.sms.services.universal.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DropdownService {

    private final CategoryService categoryService;
    private final CastService castService;
    private final ProvinceService provinceService;
    private final CityService cityService;
    private final GradeService gradeService;
    private final SectionService sectionService;
    private final MediumService mediumService;
    private final BankService bankService;

    @Autowired
    public DropdownService(CategoryService categoryService, CastService castService, ProvinceService provinceService, CityService cityService,
                           GradeService gradeService, SectionService sectionService, MediumService mediumService, BankService bankService){
        this.categoryService = categoryService;
        this.castService = castService;
        this.provinceService = provinceService;
        this.cityService = cityService;
        this.gradeService = gradeService;
        this.sectionService = sectionService;
        this.mediumService = mediumService;
        this.bankService = bankService;
    }


    public Map<String, String> getRelationships() {
        Map<String, String> relationships = new LinkedHashMap<>();
        relationships.put("Parent", "Parent");
        relationships.put("Sibling", "Sibling");
        relationships.put("Friend", "Friend");
        relationships.put("No Preference", "No Preference");
        return relationships;
    }

    public List<String> getBloodGroups() {
        return Arrays.asList("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-", "No Preference");
    }

    public List<String> getBodyTypes() {
        return Arrays.asList("Normal", "Blind", "Physically Challenged", "Other");
    }

    public List<String> getReligions(){
        return Arrays.asList(
                "Christianity",
                "Islam",
                "Hinduism",
                "Buddhism",
                "Judaism",
                "Sikhism",
                "Bahá'í Faith",
                "Jainism",
                "Shinto",
                "Taoism",
                "Confucianism",
                "Zoroastrianism",
                "Rastafarianism",
                "Paganism",
                "New Age",
                "Unitarian Universalism",
                "Scientology",
                "Druze",
                "Caodaism",
                "Tenrikyo",
                "Falun Gong",
                "African Traditional Religions",
                "Native American Religions",
                "Aboriginal Australian Religions",
                "Chinese Folk Religion",
                "Atheism",
                "Agnosticism",
                "Humanism",
                "Secularism",
                "No Preference"
        );
    }

    // Mock methods for other dropdowns
    public List<Category> getCategories() {
        // Return list of categories from database or mock data
        return categoryService.getAllCategories();
    }

    public List<Cast> getCasts() {
        // Return list of casts from database or mock data
        return castService.getAllCasts();
    }

    public List<Province> getProvinces() {
        // Return list of provinces from database or mock data
        return provinceService.getAllProvince();
    }

    public List<City> getCities(Long province_id) {
        // Return list of cities from database or mock data
        return cityService.getAllCities(province_id);
    }

    public List<Grade> getGrades() {
        // Return list of grades from database or mock data
        return gradeService.getAllGrades();
    }

    public List<Section> getSections() {
        // Return list of sections from database or mock data
        return sectionService.getAllSections();
    }

    public List<Medium> getMediums() {
        // Return list of mediums from database or mock data
        return mediumService.getAllMediums();
    }

    public List<Bank> getBanks() {
        // Return list of banks from database or mock data
        return bankService.getAllBanks();
    }
}
