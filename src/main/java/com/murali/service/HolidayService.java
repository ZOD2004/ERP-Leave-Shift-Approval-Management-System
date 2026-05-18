package com.murali.service;

import com.murali.entity.Holiday;
import com.murali.repository.HolidayRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HolidayService {
    private final HolidayRepository holidayRepository;

    public HolidayService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    public List<Holiday> getAllHolidays(){
        return holidayRepository.findAll();
    }
    public void saveHoliday(Holiday holiday){
        holidayRepository.save(holiday);
    }
    public void deleteHoliday(Long id){
        holidayRepository.deleteById(id);
    }

}
