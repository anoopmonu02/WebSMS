package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.Province;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProvinceRepository extends JpaRepository<Province, Long> {
    Optional<Province> findByProvinceNameIgnoreCase(String provinceName);
}
