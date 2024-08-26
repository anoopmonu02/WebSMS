package com.smsweb.sms.repositories.users;


import com.smsweb.sms.models.Users.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    UserEntity findByUsername(String username);

    boolean existsByUsername(String superAdminUsername);


    UserEntity findByEmail(String email);
}
