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

    /**
     * Returns all users who are employees of a given school, with roles eagerly fetched.
     * Used by PermissionAdminController to scope the user list when ROLE_ADMIN is logged in,
     * so the admin only sees and manages users belonging to their own school.
     */
    @Query("SELECT DISTINCT u FROM UserEntity u JOIN FETCH u.roles JOIN u.employee e WHERE e.school.id = :schoolId")
    List<UserEntity> findAllBySchoolIdWithRoles(@Param("schoolId") Long schoolId);
}
