package com.smsweb.sms.services.universal;

import com.smsweb.sms.models.universal.City;
import com.smsweb.sms.repositories.universal.CityRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CityService {
    private CityRepository cityRepository;

    public CityService(CityRepository cityRepository){
        this.cityRepository = cityRepository;
    }

    public List<City> getAllCities(Long provinceId){
        return cityRepository.findByProvinceId(provinceId);
    }
}
