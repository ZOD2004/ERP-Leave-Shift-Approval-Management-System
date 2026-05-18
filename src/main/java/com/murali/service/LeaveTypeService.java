package com.murali.service;

import com.murali.entity.LeaveType;
import com.murali.exception.LeaveTypeNotFoundException;
import com.murali.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
    }

    public List<LeaveType> getAllLeaveTypes() {
        return leaveTypeRepository.findAll();
    }

    public void addLeaveType(LeaveType leaveType) {
        leaveTypeRepository.save(leaveType);
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

    public List<LeaveType> search(String searchTerm) {
        return leaveTypeRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(searchTerm,searchTerm);
    }
    @Transactional(readOnly = true)
    public List<LeaveType> getAvailableLeaveTypes() {
        return leaveTypeRepository.findAll();
    }
    @Transactional(readOnly = true)
    public LeaveType getLeaveTypeByCode(String code) {
        return leaveTypeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Leave type code not found: " + code));
    }
}
