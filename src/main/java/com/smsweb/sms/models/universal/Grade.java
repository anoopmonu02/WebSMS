package com.smsweb.sms.models.universal;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Entity
public class Grade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Grade name should not blank")
    @Size(max = 100, message = "Grade should not exceed 100 chars")
    @Column(unique = true)
    private String gradeName;
}
