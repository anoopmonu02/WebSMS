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
    private final MonthMasterService monthMasterService;

    @Autowired
    public DropdownService(CategoryService categoryService, CastService castService, ProvinceService provinceService, CityService cityService,
                           GradeService gradeService, SectionService sectionService, MediumService mediumService, BankService bankService, MonthMasterService monthMasterService){
        this.categoryService = categoryService;
        this.castService = castService;
        this.provinceService = provinceService;
        this.cityService = cityService;
        this.gradeService = gradeService;
        this.sectionService = sectionService;
        this.mediumService = mediumService;
        this.bankService = bankService;
        this.monthMasterService = monthMasterService;
    }


    public Map<String, String> getRelationships() {
        Map<String, String> relationships = new LinkedHashMap<>();
        relationships.put("Parent", "PARENT");
        relationships.put("Sibling", "SIBLING");
        relationships.put("Friend", "FRIEND");
        relationships.put("No Preference", "NO PREFERENCE");
        return relationships;
    }

    public List<String> getBloodGroups() {
        return Arrays.asList("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-", "NO PREFERENCE");
    }

    public List<String> getBodyTypes() {
        return Arrays.asList("NORMAL", "BLIND", "PHYSICALLY CHALLENGED", "OTHER");
    }

    public List<String> getReligions(){
        return Arrays.asList(
                "CHRISTIANITY",
                "ISLAM",
                "HINDUISM",
                "BUDDHISM",
                "JUDAISM",
                "SIKHISM",
                "BAHÁ'Í FAITH",
                "JAINISM",
                "SHINTO",
                "TAOISM",
                "CONFUCIANISM",
                "ZOROASTRIANISM",
                "RASTAFARIANISM",
                "PAGANISM",
                "NEW AGE",
                "UNITARIAN UNIVERSALISM",
                "SCIENTOLOGY",
                "DRUZE",
                "CAODAISM",
                "TENRIKYO",
                "FALUN GONG",
                "AFRICAN TRADITIONAL RELIGIONS",
                "NATIVE AMERICAN RELIGIONS",
                "ABORIGINAL AUSTRALIAN RELIGIONS",
                "CHINESE FOLK RELIGION",
                "ATHEISM",
                "AGNOSTICISM",
                "HUMANISM",
                "SECULARISM",
                "NO PREFERENCE",
                "OTHER"
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

    public List<MonthMaster> getMonths() {
        // Return list of months from database or mock data
        return monthMasterService.getAllMonths();
    }
}
