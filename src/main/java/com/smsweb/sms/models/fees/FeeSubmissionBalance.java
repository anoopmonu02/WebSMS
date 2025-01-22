package com.smsweb.sms.models.fees;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smsweb.sms.models.student.Student;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import lombok.Data;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;


@Data
@Entity
public class FeeSubmissionBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JsonBackReference
    @ToString.Exclude
    private FeeSubmission feeSubmission;

    @Digits(integer = 10, fraction = 2)
    private BigDecimal balanceAmount = BigDecimal.ZERO;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date feeDate;

    private String status = "Active";

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;
}
