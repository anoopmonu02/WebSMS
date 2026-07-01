package com.smsweb.sms.services.universal;

import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.universal.Section;
import com.smsweb.sms.repositories.universal.SectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class SectionService {
    private static final Logger log = LoggerFactory.getLogger(SectionService.class);

    private final SectionRepository sectionRepository;

    @Autowired
    public SectionService(SectionRepository sectionRepository) {
        this.sectionRepository = sectionRepository;
    }

    public List<Section> getAllSections() {
        return sectionRepository.findAll();
    }

    public Section saveSection(Section section) {
        log.info("Inside saveSection");
        try{
            return sectionRepository.save(section);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Section already saved ",de);
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to save section: "+e.getLocalizedMessage());
        }
    }

    public Optional<Section> getSectionById(Long id) {
        return sectionRepository.findById(id);
    }

    public void deleteSection(Long id) {
        log.info("Inside deleteSection");
        sectionRepository.deleteById(id);
    }
}
