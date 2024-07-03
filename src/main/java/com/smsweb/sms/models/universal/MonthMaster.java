package com.smsweb.sms.models.universal;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Entity
public class MonthMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Month name is mandatory")
    @Column(unique = true)
    @Size(max = 20)
    private String monthName;

    @Size(max = 10)
    private String monthCode;
}
