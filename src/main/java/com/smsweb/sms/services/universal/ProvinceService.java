package com.smsweb.sms.services.universal;

import com.smsweb.sms.models.universal.Province;
import com.smsweb.sms.repositories.universal.ProvinceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProvinceService {
    private ProvinceRepository provinceRepository;

    @Autowired
    public ProvinceService(ProvinceRepository provinceRepository){
        this.provinceRepository = provinceRepository;
    }

    public List<Province> getAllProvince(){
        return provinceRepository.findAll();
    }
}
