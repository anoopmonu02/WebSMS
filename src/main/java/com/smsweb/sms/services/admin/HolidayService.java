package com.smsweb.sms.services.admin;

import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.models.admin.Holiday;
import com.smsweb.sms.repositories.admin.HolidayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;

    @Autowired
    public HolidayService(HolidayRepository holidayRepository){

        this.holidayRepository = holidayRepository;
    }

    public List<Holiday> getAllHoliday(Long academic_year, Long school_id){
        return holidayRepository.findAllByAcademicYear_IdAndSchool_IdOrderByIdAsc(academic_year, school_id);
    }

    public List<Holiday> getAllHolidayStartsFromToday(Long academic_year, Long school_id){
        Date today = new Date();
        return holidayRepository.findAllByAcademicYear_IdAndSchool_IdAndHolidayStartDateAfterOrderByIdAsc(academic_year, school_id, today);
    }

    public Holiday save(Holiday holiday){
        try{
            return holidayRepository.save(holiday);
        }catch(DataIntegrityViolationException de){
            throw new UniqueConstraintsException("Holiday already saved ",de);
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to save holiday", e);
        }
    }

    public String delete(Long id){
        try{
            holidayRepository.deleteById(id);
        }catch(Exception e){
            throw new RuntimeException("Error in deletion "+e.getLocalizedMessage());
        }
        return "success";
    }
}
