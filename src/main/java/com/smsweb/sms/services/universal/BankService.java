package com.smsweb.sms.services.universal;

import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.universal.Bank;
import com.smsweb.sms.repositories.universal.BankRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class BankService {
    private static final Logger log = LoggerFactory.getLogger(BankService.class);

    private final BankRepository bankRepository;

    @Autowired
    public BankService(BankRepository bankRepository){
        this.bankRepository = bankRepository;
    }

    public List<Bank> getAllBanks() {
        return bankRepository.findAll();
    }

    public Bank saveBank(Bank bank) {
        log.info("Inside saveBank");
        try{
            return bankRepository.save(bank);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Bank already saved ",de);
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to save bank: "+e.getLocalizedMessage());
        }
    }

    public Optional<Bank> getBankById(Long id) {
        return bankRepository.findById(id);
    }

    public void deleteBank(Long id) {
        log.info("Inside deleteBank");
        bankRepository.deleteById(id);
    }
}
