package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.FamilyAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyAccountRepository extends JpaRepository<FamilyAccount, Long> {

    Optional<FamilyAccount> findByMobileAndStatus(String mobile, String status);

    Optional<FamilyAccount> findByMobile(String mobile);

    boolean existsByMobile(String mobile);

    /**
     * Same rows as findAll() but JOIN FETCHes students in the same query (feature:
     * Mobile Users admin screen perf fix). Plain findAll() + account.getStudents()
     * per row was one extra SELECT per family account (students is LAZY) — with a
     * few hundred/thousand family accounts that alone was a big chunk of the page's
     * load time. DISTINCT because the join multiplies rows once per student.
     */
    @Query("SELECT DISTINCT f FROM FamilyAccount f LEFT JOIN FETCH f.students")
    List<FamilyAccount> findAllWithStudents();
}
