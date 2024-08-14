package com.smsweb.sms.services.universal;

import com.smsweb.sms.models.universal.Discounthead;
import com.smsweb.sms.repositories.universal.DiscountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DiscountService {
    private final DiscountRepository discountRepository;

    @Autowired
    public DiscountService(DiscountRepository discountRepository){
        this.discountRepository = discountRepository;
    }

    public List<Discounthead> getAllDiscountheads(){
        //return discountRepository.findAll(Sort.by(Sort.DEFAULT_DIRECTION, "id"));
        return discountRepository.findByDiscountNameNotContains("Sibling");
    }

    public void saveDiscounthead(Discounthead discounthead) {
        discountRepository.save(discounthead);
    }

    public Optional<Discounthead> getDiscountheadById(Long id) {
        return discountRepository.findById(id);
    }

    public Discounthead findByDiscountName(String discountName){
        return discountRepository.findByDiscountName(discountName);
    }

    public void deleteDiscounthead(Long id) {
        discountRepository.deleteById(id);
    }
}
