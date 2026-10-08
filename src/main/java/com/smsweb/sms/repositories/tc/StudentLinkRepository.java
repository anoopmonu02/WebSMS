package com.smsweb.sms.repositories.tc;

import com.smsweb.sms.models.tc.StudentLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentLinkRepository extends JpaRepository<StudentLink, Long> {

    /** The link that created this (new) record, if it was a re-registration. */
    Optional<StudentLink> findByNewStudent_Id(Long newStudentId);

    /** Links created from this (old) record; a student can be re-registered more than once. */
    List<StudentLink> findByOldStudent_Id(Long oldStudentId);
}
