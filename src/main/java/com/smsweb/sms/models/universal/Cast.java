package com.smsweb.sms.models.universal;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Entity
public class Cast {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Cast name should not blank")
    @Size(max = 100, message = "Cast should not exceed 100 chars")
    @Column(unique = true)
    private String castName;
}
