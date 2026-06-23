package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SectionRepository extends JpaRepository<Section, Long> {
    Optional<Section> findBySectionNameIgnoreCase(String sectionName);
}
