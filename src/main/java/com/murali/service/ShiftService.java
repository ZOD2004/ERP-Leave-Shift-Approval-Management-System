package com.murali.service;

import com.murali.entity.Shift;
import com.murali.repository.ShiftAssignmentRepository;
import com.murali.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AuditLogService auditLoggingService;

    public List<Shift> getShifts(){
        return shiftRepository.findAll();
    }

    @Transactional
    public void deleteShift(Long id){
        if (shiftAssignmentRepository.existsByShiftId(id)) {
            throw new IllegalStateException("Cannot delete this shift because it is currently assigned to one or more employees. Please reassign them first.");
        }

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

    @Transactional
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
        validateAndPrepareShift(shift);

        Shift savedShift = shiftRepository.save(shift);

        String newState = String.format("{ \"name\": \"%s\", \"startTime\": \"%s\", \"endTime\": \"%s\", \"crossesMidnight\": %b }",
                savedShift.getName(), savedShift.getStartTime(), savedShift.getEndTime(), savedShift.getCrossesMidnight());
        String action = isNew ? "CREATED" : "UPDATED";

        log.info("Shift {} successfully. ID: {}", action, savedShift.getId());
        auditLoggingService.saveAuditLog(savedShift.getId(), action, "shifts", oldState, newState);
    }

    private void validateAndPrepareShift(Shift shift) {
        shiftRepository.findByNameIgnoreCase(shift.getName()).ifPresent(existingShift -> {
            if (shift.getId() == null || !existingShift.getId().equals(shift.getId())) {
                throw new IllegalArgumentException("A shift with the name '" + shift.getName() + "' already exists.");
            }
        });

        if (Boolean.TRUE.equals(shift.getIsRotationalShift())) {
            shift.setCrossesMidnight(false);

            shift.setStartTime(LocalTime.MIDNIGHT);
            shift.setEndTime(LocalTime.MIDNIGHT);

            return;
        }

        if (shift.getWorkingDays() == null || shift.getWorkingDays().isEmpty()) {
            throw new IllegalArgumentException("At least one working day is required.");
        }

        if (shift.getStartTime() == null || shift.getEndTime() == null) {
            throw new IllegalArgumentException("Start time and end time are required for standard shifts.");
        }

        if (shift.getStartTime().equals(shift.getEndTime())) {
            throw new IllegalArgumentException("Start time and end time cannot be the same.");
        }

        boolean crosses = shift.getEndTime().isBefore(shift.getStartTime());
        shift.setCrossesMidnight(crosses);
    }

    public Optional<Shift> getShiftById(Long id){
        return shiftRepository.findById(id);
    }

    public List<Shift> search(String searchTerm) {
        return shiftRepository.findByNameContainingIgnoreCase(searchTerm);
    }

    public List<Shift> findAll() {
        return shiftRepository.findAll();
    }
    public List<Shift> getStandardShifts() {
        return shiftRepository.findStandardShifts();
    }
    @Transactional(readOnly = true)
    public Shift getShiftWithSequences(Long id) {
        Shift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shift not found"));

        if (shift.getRotationSequences() != null) {
            shift.getRotationSequences().size();
        }

        return shift;
    }
}