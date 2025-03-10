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

@Service
public class CastService {
    private final CastRepository castRepository;

    @Autowired
    public CastService(CastRepository castRepository){
        this.castRepository = castRepository;
    }

    public List<Cast> getAllCasts(){
        return castRepository.findAll(Sort.by(Sort.DEFAULT_DIRECTION, "id"));
    }

    public Cast saveCast(Cast cast) {
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
        castRepository.deleteById(id);
    }
}
