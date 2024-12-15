package com.smsweb.sms.models.fees;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.smsweb.sms.models.universal.Feehead;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;


@Data
@Entity
public class FeeSubmissionSub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JsonBackReference
    private FeeSubmission feeSubmission;

    @ManyToOne
    @JoinColumn(name = "fee_head_id")
    @NotNull(message = "Fee should be available")
    private Feehead feehead;

    @Digits(integer = 10, fraction = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    private String status="Active";
}
