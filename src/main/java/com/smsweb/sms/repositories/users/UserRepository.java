package com.smsweb.sms.repositories.users;


import com.smsweb.sms.models.Users.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    UserEntity findByUsername(String username);

    boolean existsByUsername(String superAdminUsername);


    UserEntity findByEmail(String email);

    @Query("SELECT u FROM UserEntity u JOIN FETCH u.roles")
    List<UserEntity> findAllWithRoles();
}
