package com.smsweb.sms.models.permission;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "app_screen")
@Getter @Setter @NoArgsConstructor
public class AppScreen {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String module;       // e.g. "Student", "Fees", "Admin"

    @Column(nullable = false)
    private String screenName;   // e.g. "Student List", "Fee Submission"

    @Column(nullable = false, unique = true)
    private String screenKey;    // e.g. "STUDENT_LIST", "FEE_SUBMIT"

    @Column
    private String description;  // shown in admin UI

    public AppScreen(String module, String screenName, String screenKey, String description) {
        this.module = module;
        this.screenName = screenName;
        this.screenKey = screenKey;
        this.description = description;
    }
}
