package com.smsweb.sms.services.admin;


import com.smsweb.sms.exceptions.ObjectNotDeleteException;
import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.admin.FullPayment;
import com.smsweb.sms.repositories.admin.FullpaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FullpaymentService {
    private final FullpaymentRepository fullpaymentRepository;

    @Autowired
    public FullpaymentService(FullpaymentRepository fullpaymentRepository){
        this.fullpaymentRepository = fullpaymentRepository;
    }

    public List<FullPayment> getAllFullPayments(Long school_id, Long academic_id){
        return fullpaymentRepository.findAllBySchool_IdAndAcademicYear_Id(school_id, academic_id);
    }

    public Optional<FullPayment> getFullPaymentById(Long id){
        return fullpaymentRepository.findById(id);
    }

    public FullPayment save(FullPayment fullPayment){
        try{
            return fullpaymentRepository.save(fullPayment);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Data already exists.",de);
        } catch(Exception e){
            throw new ObjectNotSaveException("Error in saving.", e);
        }
    }

    public String deleteFullPayment(Long id){
        try{
            fullpaymentRepository.deleteById(id);
            return "success";
        } catch(Exception e){
            throw new ObjectNotDeleteException("Unable to delete Full payment discount", e);
        }
    }

}
