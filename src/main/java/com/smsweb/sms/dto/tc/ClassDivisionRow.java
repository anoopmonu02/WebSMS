package com.smsweb.sms.dto.tc;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One row of the Class Groups page (a group, its register numbers and its mapped grades). */
@Getter
@Setter
public class ClassDivisionRow {
    private UUID uuid;
    private String name;
    private String description;
    private String tcFormat;
    private Long startNumber;
    private Long nextNumber;
    private String status;
    /** true once a TC exists in this group: the start number can no longer change. */
    private boolean startLocked;
    private List<Long> gradeIds = new ArrayList<>();
    private List<String> gradeNames = new ArrayList<>();
    /** Grades this group may hold: its own (checked) plus the grades not in any group (unchecked). */
    private List<GradeOption> gradeOptions = new ArrayList<>();

    @Getter
    public static class GradeOption {
        private final Long id;
        private final String name;
        private final boolean selected;

        public GradeOption(Long id, String name, boolean selected) {
            this.id = id;
            this.name = name;
            this.selected = selected;
        }
    }
}
