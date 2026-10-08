package com.smsweb.sms.models.tc;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.universal.Grade;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Maps a grade to exactly one class group per school (grade, then group, then the group's TC
 * format). Plan section 5.2.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "class_division_grade",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_class_division_grade_school_grade", columnNames = {"school_id", "grade_id"})
        },
        indexes = {
                @Index(name = "idx_class_division_grade_division", columnList = "division_id")
        })
public class ClassDivisionGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false, updatable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "division_id", nullable = false)
    private ClassDivision division;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false, updatable = false)
    private Grade grade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(name = "creation_date", updatable = false)
    private LocalDateTime creationDate;
}
