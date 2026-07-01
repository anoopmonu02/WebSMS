package com.smsweb.sms.services.universal;

import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.universal.Cast;
import com.smsweb.sms.repositories.universal.CastRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class CastService {
    private static final Logger log = LoggerFactory.getLogger(CastService.class);

    private final CastRepository castRepository;

    @Autowired
    public CastService(CastRepository castRepository){
        this.castRepository = castRepository;
    }

    public List<Cast> getAllCasts(){
        return castRepository.findAll(Sort.by(Sort.DEFAULT_DIRECTION, "id"));
    }

    public Cast saveCast(Cast cast) {
        log.info("Inside saveCast");
        try{
            return castRepository.save(cast);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Cast already saved ",de);
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to save cast: "+e.getLocalizedMessage());
        }
    }

    public Optional<Cast> getCastById(Long id) {
        return castRepository.findById(id);
    }

    public void deleteCast(Long id) {
        log.info("Inside deleteCast");
        castRepository.deleteById(id);
    }
}
