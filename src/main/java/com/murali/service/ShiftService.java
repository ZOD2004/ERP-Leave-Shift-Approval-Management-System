package com.murali.service;

import com.murali.entity.Shift;
import com.murali.exception.UserNotFoundException;
import com.murali.repository.ShiftRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ShiftService {
    private final ShiftRepository shiftRepository;

    public ShiftService(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    public List<Shift> getShifts(){
        return shiftRepository.findAll();
    }

    public void addShift(Shift shift){
        shiftRepository.save(shift);
    }

    public void deleteShift(Long id){
        shiftRepository.deleteById(id);
    }
    public Shift updateShift(Long id,Shift shift){
        Optional<Shift> optShift = shiftRepository.findById(id);
        Shift currShift;
        if (optShift.isPresent()){
            currShift=optShift.get();
        }else{
            throw new UserNotFoundException("User with id :"+id+" is not found");
        }
        currShift.setShiftType(shift.getShiftType());
        currShift.setName(shift.getName());
        currShift.setEndTime(shift.getEndTime());
        currShift.setStartTime(shift.getStartTime());
        return shiftRepository.save(shift);
    }

    public Optional<Shift> getShiftById(Long id){
        return shiftRepository.findById(id);
    }

    public List<Shift> search(String searchTerm) {
        return shiftRepository.findByNameContainingIgnoreCase(searchTerm);
    }
}
