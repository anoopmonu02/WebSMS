package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.FamilyAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FamilyAccountRepository extends JpaRepository<FamilyAccount, Long> {

    Optional<FamilyAccount> findByMobileAndStatus(String mobile, String status);

    Optional<FamilyAccount> findByMobile(String mobile);

    boolean existsByMobile(String mobile);
}
