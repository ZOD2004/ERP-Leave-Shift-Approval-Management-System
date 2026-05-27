package com.murali.service;

import com.murali.entity.LeaveType;
import com.murali.exception.LeaveTypeNotFoundException;
import com.murali.repository.LeaveTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final AuditLogService auditLoggingService;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository,
                            AuditLogService auditLoggingService) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.auditLoggingService = auditLoggingService;
    }

    public List<LeaveType> getAllLeaveTypes() {
        return leaveTypeRepository.findAll();
    }

    public void addLeaveType(LeaveType leaveType) {
        LeaveType savedLeaveType = leaveTypeRepository.save(leaveType);

        String newState = String.format("{ \"name\": \"%s\", \"code\": \"%s\", \"paid\": %b, \"maxDaysPerYear\": %s }",
                savedLeaveType.getName(), savedLeaveType.getCode(), savedLeaveType.getPaid(), savedLeaveType.getMaxDaysPerYear());

        log.info("LeaveType CREATED successfully. ID: {}", savedLeaveType.getId());
        auditLoggingService.saveAuditLog(savedLeaveType.getId(), "CREATED", "leave_types", null, newState);
    }

    public void deleteLeaveType(Long id) {
        String oldState = null;
        Optional<LeaveType> existingOpt = leaveTypeRepository.findById(id);
        if (existingOpt.isPresent()) {
            LeaveType existing = existingOpt.get();
            oldState = String.format("{ \"name\": \"%s\", \"code\": \"%s\", \"paid\": %b, \"maxDaysPerYear\": %s }",
                    existing.getName(), existing.getCode(), existing.getPaid(), existing.getMaxDaysPerYear());
        }

        leaveTypeRepository.deleteById(id);

        log.info("LeaveType DELETED successfully. ID: {}", id);
        auditLoggingService.saveAuditLog(id, "DELETED", "leave_types", oldState, null);
    }

    public LeaveType editLeaveType(Long id, LeaveType leaveType) {
        Optional<LeaveType> optLeaveType = leaveTypeRepository.findById(id);
        LeaveType currLeaveType;

        if (optLeaveType.isPresent()) {
            currLeaveType = optLeaveType.get();
        } else {
            throw new LeaveTypeNotFoundException("LeaveType with id: " + id + " is not found");
        }

        String oldState = String.format("{ \"name\": \"%s\", \"code\": \"%s\", \"paid\": %b, \"maxDaysPerYear\": %s }",
                currLeaveType.getName(), currLeaveType.getCode(), currLeaveType.getPaid(), currLeaveType.getMaxDaysPerYear());

        currLeaveType.setName(leaveType.getName());
        currLeaveType.setCode(leaveType.getCode());
        currLeaveType.setPaid(leaveType.getPaid());
        currLeaveType.setMaxDaysPerYear(leaveType.getMaxDaysPerYear());

        LeaveType savedLeaveType = leaveTypeRepository.save(currLeaveType);

        String newState = String.format("{ \"name\": \"%s\", \"code\": \"%s\", \"paid\": %b, \"maxDaysPerYear\": %s }",
                savedLeaveType.getName(), savedLeaveType.getCode(), savedLeaveType.getPaid(), savedLeaveType.getMaxDaysPerYear());

        log.info("LeaveType UPDATED successfully. ID: {}", savedLeaveType.getId());
        auditLoggingService.saveAuditLog(savedLeaveType.getId(), "UPDATED", "leave_types", oldState, newState);

        return savedLeaveType;
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