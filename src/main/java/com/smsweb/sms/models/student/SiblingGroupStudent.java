package com.smsweb.sms.models.student;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Entity
public class SiblingGroupStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "academic_student_id")
    @NotNull(message = "Student should be available")
    private AcademicStudent academicStudent;

    @ManyToOne
    @JoinColumn(name = "sibling_group_id")
    @NotNull(message = "Sibling-group should be available")
    private SiblingGroup siblingGroup;
}
