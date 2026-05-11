package com.murali.service;

import com.murali.entity.LeaveType;
import com.murali.exception.LeaveTypeNotFoundException;
import com.murali.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
    }

    public List<LeaveType> getLeaveTypes() {
        return leaveTypeRepository.findAll();
    }

    public LeaveType addLeaveType(LeaveType leaveType) {
        return leaveTypeRepository.save(leaveType);
    }

    public void deleteLeaveType(Long id) {
        leaveTypeRepository.deleteById(id);
    }

    public LeaveType editLeaveType(Long id, LeaveType leaveType) {
        Optional<LeaveType> optLeaveType = leaveTypeRepository.findById(id);
        LeaveType currLeaveType;

        if (optLeaveType.isPresent()) {
            currLeaveType = optLeaveType.get();
        } else {
            throw new LeaveTypeNotFoundException("LeaveType with id: " + id + " is not found");
        }
        currLeaveType.setName(leaveType.getName());
        currLeaveType.setCode(leaveType.getCode());
        currLeaveType.setPaid(leaveType.getPaid());
        currLeaveType.setMaxDaysPerYear(leaveType.getMaxDaysPerYear());

        return leaveTypeRepository.save(currLeaveType);
    }
}
