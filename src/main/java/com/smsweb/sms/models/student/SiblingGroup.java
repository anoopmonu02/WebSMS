package com.smsweb.sms.models.student;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.List;

@Data
@Entity
@Table(uniqueConstraints = {@UniqueConstraint(name = "uk_student_sibling_grp",columnNames = {"groupName", "academic_year_id", "school_id"})})
@ToString(exclude = {"siblingGroupStudents","createdBy", "updatedBy"})
public class SiblingGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Group name should not blank")
    @Size(max = 100, message = "Group name should not exceed 100 chars")
    private String groupName;

    @Column(columnDefinition = "TEXT")
    @Size(max = 500, message = "Description should not exceed 500 chars")
    private String description;
    private String status = "Active";

    @ManyToOne
    @JoinColumn(name = "school_id")
    @NotNull(message = "School should be available")
    private School school;

    @ManyToOne
    @JoinColumn(name = "academic_year_id")
    @NotNull(message = "Academic-Year should be available")
    private AcademicYear academicYear;

    @OneToMany(mappedBy = "siblingGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<SiblingGroupStudent> siblingGroupStudents;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date lastUpdated;
    //TODO-will add 2 more attributes - createdBy, updatedBy


    public void addStudent(SiblingGroupStudent student) {
        siblingGroupStudents.add(student);
        student.setSiblingGroup(this);
    }

    public void removeStudent(SiblingGroupStudent student) {
        siblingGroupStudents.remove(student);
        student.setSiblingGroup(null);
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    @JsonIgnore
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @JsonIgnore
    private UserEntity updatedBy;
}
