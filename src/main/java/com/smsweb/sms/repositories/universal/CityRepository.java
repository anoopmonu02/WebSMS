package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.models.universal.Province;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findByProvinceId(Long provinceId);
}
