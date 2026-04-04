package com.smsweb.sms.repositories.users;


import com.smsweb.sms.models.Users.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    UserEntity findByUsername(String username);

    boolean existsByUsername(String superAdminUsername);


    UserEntity findByEmail(String email);

    @Query("SELECT u FROM UserEntity u JOIN FETCH u.roles")
    List<UserEntity> findAllWithRoles();

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.roles WHERE u.username = :username")
    Optional<UserEntity> findByUsernameWithRoles(@Param("username") String username);

    @Query("""
    SELECT u FROM UserEntity u
    LEFT JOIN FETCH u.roles
    LEFT JOIN FETCH u.employee emp
    LEFT JOIN FETCH emp.school empSchool
    LEFT JOIN FETCH empSchool.customer
    LEFT JOIN FETCH u.student stu
    LEFT JOIN FETCH stu.school stuSchool
    LEFT JOIN FETCH stuSchool.customer
    LEFT JOIN FETCH stu.academicYear
    WHERE u.username = :username
""")
    UserEntity findByUsernameWithDetails(@Param("username") String username);
}
