package com.smsweb.sms.models.universal;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Entity
public class Medium {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Medium name is mandatory")
    @Column(unique = true)
    @Size(max=100, message = "Medium name should not more than 100 chars")
    private String mediumName;
}
