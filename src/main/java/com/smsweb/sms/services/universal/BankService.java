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

@Service
public class BankService {
    private final BankRepository bankRepository;

    @Autowired
    public BankService(BankRepository bankRepository){
        this.bankRepository = bankRepository;
    }

    public List<Bank> getAllBanks() {
        return bankRepository.findAll();
    }

    public Bank saveBank(Bank bank) {
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
        bankRepository.deleteById(id);
    }
}
