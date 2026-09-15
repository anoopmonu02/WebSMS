package com.smsweb.sms.services.student;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of the field groups shown in the "Select Data" dropdown on the
 * "Update Student Details (Group-wise)" page (Students menu). Each entry
 * drives both what StudentBulkUpdateController renders and what
 * StudentBulkUpdateService reads/writes on save.
 *
 * NEW, isolated class — does not modify any existing entity, controller,
 * or service. Student Image is deliberately NOT included yet: its upload
 * mechanics (per-row instant upload vs. Save-button batching) are still
 * pending confirmation and will be added as its own group once settled.
 */
public class StudentFieldGroupRegistry {

    public enum Widget {
        TEXT, TEXTAREA, SELECT, SELECT_LOOKUP, SELECT_CITY, NUMBER, DATE, EMAIL, CHECKBOX
    }

    public static class FieldDef {
        private final String key;
        private final String label;
        private final Widget widget;

        public FieldDef(String key, String label, Widget widget) {
            this.key = key;
            this.label = label;
            this.widget = widget;
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public Widget getWidget() { return widget; }
    }

    public static class GroupDef {
        private final String key;
        private final String label;
        private final List<FieldDef> fields;

        public GroupDef(String key, String label, List<FieldDef> fields) {
            this.key = key;
            this.label = label;
            this.fields = fields;
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public List<FieldDef> getFields() { return fields; }
    }

    private static final List<GroupDef> GROUPS = new ArrayList<>();

    static {
        GROUPS.add(new GroupDef("SR_NO", "SR No", List.of(
                new FieldDef("classSrNo", "SR No", Widget.TEXT)
        )));
        GROUPS.add(new GroupDef("GENDER_QUALIFICATION", "Gender, Father Qualification, Mother Qualification", List.of(
                new FieldDef("gender", "Gender", Widget.SELECT),
                new FieldDef("fatherQualification", "Father Qualification", Widget.SELECT),
                new FieldDef("motherQualification", "Mother Qualification", Widget.SELECT)
        )));
        GROUPS.add(new GroupDef("RELIGION_CATEGORY_CASTE", "Religion, Category, Caste", List.of(
                new FieldDef("religion", "Religion", Widget.SELECT),
                new FieldDef("category", "Category", Widget.SELECT_LOOKUP),
                new FieldDef("cast", "Caste", Widget.SELECT_LOOKUP)
        )));
        GROUPS.add(new GroupDef("ADDRESS_CITY_PINCODE", "Address, Province, City, Pincode", List.of(
                new FieldDef("address", "Address", Widget.TEXTAREA),
                new FieldDef("province", "Province", Widget.SELECT_LOOKUP),
                new FieldDef("city", "City", Widget.SELECT_CITY),
                new FieldDef("pincode", "Pincode", Widget.TEXT)
        )));
        GROUPS.add(new GroupDef("BANK_BRANCH_IFSC_ACCOUNT", "Bank, Branch, IFSC, Account No", List.of(
                new FieldDef("bank", "Bank", Widget.SELECT_LOOKUP),
                new FieldDef("branchName", "Branch", Widget.TEXT),
                new FieldDef("ifscCode", "IFSC", Widget.TEXT),
                new FieldDef("accountNo", "Account No", Widget.TEXT)
        )));
        GROUPS.add(new GroupDef("HEIGHT_WEIGHT", "Height, Weight", List.of(
                new FieldDef("height", "Height", Widget.NUMBER),
                new FieldDef("weight", "Weight", Widget.NUMBER)
        )));
        GROUPS.add(new GroupDef("HEALTH_EYE_ISSUE", "Body Type, Have Health Issue, Health Issue Description, Have Eye Issue", List.of(
                new FieldDef("bodyType", "Body Type", Widget.SELECT),
                new FieldDef("haveHealthIssues", "Have Health Issue", Widget.CHECKBOX),
                new FieldDef("healthIssueDescription", "Health Issue Description", Widget.TEXT),
                new FieldDef("haveEyeIssue", "Have Eye Issue", Widget.CHECKBOX)
        )));
        GROUPS.add(new GroupDef("MOBILE1_MOBILE2", "Mobile1, Mobile2", List.of(
                new FieldDef("mobile1", "Mobile1", Widget.TEXT),
                new FieldDef("mobile2", "Mobile2", Widget.TEXT)
        )));
        GROUPS.add(new GroupDef("DOB", "DOB", List.of(
                new FieldDef("dob", "DOB", Widget.DATE)
        )));
        GROUPS.add(new GroupDef("FATHER_OCCUPATION", "Father Occupation", List.of(
                new FieldDef("fatherOccupation", "Father Occupation", Widget.TEXT)
        )));
        GROUPS.add(new GroupDef("EMAIL", "Email", List.of(
                new FieldDef("email", "Email", Widget.EMAIL)
        )));
        GROUPS.add(new GroupDef("DISTANCE_FROM_SCHOOL", "Distance from School", List.of(
                new FieldDef("distanceFromSchool", "Distance from School", Widget.NUMBER)
        )));
        GROUPS.add(new GroupDef("AADHAR_NO", "Aadhar Number", List.of(
                new FieldDef("aadharNo", "Aadhar Number", Widget.TEXT)
        )));
        GROUPS.add(new GroupDef("PEN_NO", "PEN Number", List.of(
                new FieldDef("penNo", "PEN Number", Widget.TEXT)
        )));
        GROUPS.add(new GroupDef("APAAR_ID", "Apaar Id", List.of(
                new FieldDef("apaarId", "Apaar Id", Widget.TEXT)
        )));
        GROUPS.add(new GroupDef("BLOOD_GROUP", "Blood Group", List.of(
                new FieldDef("bloodGroup", "Blood Group", Widget.SELECT)
        )));
    }

    private StudentFieldGroupRegistry() { }

    public static List<GroupDef> getAllGroups() {
        return GROUPS;
    }

    public static GroupDef getByKey(String key) {
        if (key == null) return null;
        for (GroupDef g : GROUPS) {
            if (g.getKey().equals(key)) return g;
        }
        return null;
    }
}
