package com.smsweb.sms.services.student;

import com.smsweb.sms.models.student.SiblingDiscount;
import com.smsweb.sms.repositories.student.SiblingDiscountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SiblingDiscountService {
    private SiblingDiscountRepository siblingDiscountRepository;

    @Autowired
    public SiblingDiscountService(SiblingDiscountRepository siblingDiscountRepository){
        this.siblingDiscountRepository = siblingDiscountRepository;
    }
}
