package com.smsweb.sms.models.fees;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.smsweb.sms.models.universal.MonthMaster;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Entity
public class FeeSubmissionMonths {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JsonBackReference
    private FeeSubmission feeSubmission;

    @ManyToOne
    @JoinColumn(name = "month_master_id")
    @NotNull(message = "Month should be available")
    private MonthMaster monthMaster;

    private String status="Active";
}
