package com.smsweb.sms.services.universal;

import com.smsweb.sms.models.universal.Bank;
import com.smsweb.sms.repositories.universal.BankRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    public void saveBank(Bank bank) {
        bankRepository.save(bank);
    }

    public Optional<Bank> getBankById(Long id) {
        return bankRepository.findById(id);
    }

    public void deleteBank(Long id) {
        bankRepository.deleteById(id);
    }
}
