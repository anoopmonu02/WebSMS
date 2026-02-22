package com.smsweb.sms.models.Users;

import com.smsweb.sms.models.student.Student;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username")
})
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(nullable = false)
    private Boolean enabled = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private List<Roles> roles = new ArrayList<>();

    @OneToOne(mappedBy = "userEntity", fetch = FetchType.LAZY)
    private Employee employee;

    @OneToOne(mappedBy = "userEntity", fetch = FetchType.LAZY)
    private Student student;

    public String getDisplayName() {
        if (employee != null) {
            return employee.getEmployeeName();
        }
        if (student != null) {
            return student.getStudentName();
        }
        return username;
    }
}
