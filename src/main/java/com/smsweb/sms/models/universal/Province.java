package com.smsweb.sms.models.universal;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Entity
public class Province {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    //unique, length
    @NotBlank(message = "Province name should not blank")
    @Size(max = 100)
    @Column(unique = true)
    private String provinceName;
}
