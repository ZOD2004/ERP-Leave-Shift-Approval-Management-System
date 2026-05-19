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
        currShift.setWorkingDays(shift.getWorkingDays());
        return shiftRepository.save(currShift);
    }
    public void addShift(Shift shift) {
        validateShift(shift);
        shiftRepository.save(shift);
    }
    private void validateShift(Shift shift) {

        if (shift.getWorkingDays() == null || shift.getWorkingDays().isEmpty()) {
            throw new IllegalArgumentException("At least one working day is required");
        }

        if (shift.getStartTime().equals(shift.getEndTime())) {
            throw new IllegalArgumentException("Start time and end time cannot be same");
        }
    }

    public Optional<Shift> getShiftById(Long id){
        return shiftRepository.findById(id);
    }

    public List<Shift> search(String searchTerm) {
        return shiftRepository.findByNameContainingIgnoreCase(searchTerm);
    }
}
