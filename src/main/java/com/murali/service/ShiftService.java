package com.murali.service;

import com.murali.entity.Shift;
import com.murali.exception.UserNotFoundException;
import com.murali.repository.ShiftRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final AuditLogService auditLoggingService;

    public ShiftService(ShiftRepository shiftRepository,
                        AuditLogService auditLoggingService) {
        this.shiftRepository = shiftRepository;
        this.auditLoggingService = auditLoggingService;
    }

    public List<Shift> getShifts(){
        return shiftRepository.findAll();
    }

    public void deleteShift(Long id){
        String oldState = null;
        Optional<Shift> existingOpt = shiftRepository.findById(id);
        if (existingOpt.isPresent()) {
            Shift existing = existingOpt.get();
            oldState = String.format("{ \"name\": \"%s\", \"startTime\": \"%s\", \"endTime\": \"%s\" }",
                    existing.getName(), existing.getStartTime(), existing.getEndTime());
        }

        shiftRepository.deleteById(id);

        log.info("Shift DELETED successfully. ID: {}", id);
        auditLoggingService.saveAuditLog(id, "DELETED", "shifts", oldState, null);
    }

    public void addShift(Shift shift) {
        boolean isNew = (shift.getId() == null);
        String oldState = null;

        if (!isNew) {
            Optional<Shift> existingOpt = shiftRepository.findById(shift.getId());
            if (existingOpt.isPresent()) {
                Shift existing = existingOpt.get();
                oldState = String.format("{ \"name\": \"%s\", \"startTime\": \"%s\", \"endTime\": \"%s\" }",
                        existing.getName(), existing.getStartTime(), existing.getEndTime());
            }
        }

        validateShift(shift);
        Shift savedShift = shiftRepository.save(shift);

        String newState = String.format("{ \"name\": \"%s\", \"startTime\": \"%s\", \"endTime\": \"%s\" }",
                savedShift.getName(), savedShift.getStartTime(), savedShift.getEndTime());
        String action = isNew ? "CREATED" : "UPDATED";

        log.info("Shift {} successfully. ID: {}", action, savedShift.getId());
        auditLoggingService.saveAuditLog(savedShift.getId(), action, "shifts", oldState, newState);
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