package com.smsweb.sms.repositories.automation;

import com.smsweb.sms.models.automation.AllClearMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AllClearMessageRepository extends JpaRepository<AllClearMessage, Long> {

    List<AllClearMessage> findBySchoolIdOrderByIdAsc(Long schoolId);

    long countBySchoolId(Long schoolId);

    Optional<AllClearMessage> findByIdAndSchoolId(Long id, Long schoolId);
}
