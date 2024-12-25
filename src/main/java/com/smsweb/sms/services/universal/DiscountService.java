package com.smsweb.sms.services.universal;

import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.universal.Discounthead;
import com.smsweb.sms.repositories.universal.DiscountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
        return discountRepository.findAll(Sort.by(Sort.DEFAULT_DIRECTION, "id"));
        //return discountRepository.findByDiscountNameNotContains("Sibling");
    }

    public List<Discounthead> getAllDiscountheadsExcludeSibling(){
        //return discountRepository.findAll(Sort.by(Sort.DEFAULT_DIRECTION, "id"));
        return discountRepository.findByDiscountNameNotContains("Sibling");
    }

    public Discounthead saveDiscounthead(Discounthead discounthead) {
        try{
            return discountRepository.save(discounthead);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Discount-Head already saved ",de);
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to save discount head: "+e.getLocalizedMessage());
        }
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
