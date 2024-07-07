package com.smsweb.sms.models.universal;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Entity
public class City {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "City name should not blank")
    @Size(max = 100)
    private String cityName;

    @ManyToOne
    @JoinColumn(name = "province_id")
    private Province province;
}
