package com.murali.service;

import com.murali.dto.BatchPreviewResponse;
import com.murali.dto.ShiftAssignmentDTO;
import com.murali.dto.ShiftConflictDTO;
import com.murali.entity.AuditLog;
import com.murali.entity.Employee;
import com.murali.entity.LeaveRequest;
import com.murali.entity.Shift;
import com.murali.entity.ShiftAssignment;
import com.murali.entity.enums.LeaveSession;
import com.murali.exception.EmployeeNotFoundException;
import com.murali.exception.ShiftConflictException;
import com.murali.exception.ShiftNotFoundException;
import com.murali.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShiftAssignmentService {

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftRepository shiftRepository;
    private final AttendanceSyncService attendanceSyncService;

    // --- ADDED DEPENDENCIES ---
    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;

    public ShiftAssignmentService(ShiftAssignmentRepository shiftAssignmentRepository,
                                  HolidayRepository holidayRepository,
                                  LeaveRequestRepository leaveRequestRepository,
                                  EmployeeRepository employeeRepository,
                                  ShiftRepository shiftRepository, AttendanceSyncService attendanceSyncService,
                                  AuditLogRepository auditLogRepository,
                                  SecurityService securityService) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.holidayRepository = holidayRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
        this.attendanceSyncService = attendanceSyncService;
        this.auditLogRepository = auditLogRepository;
        this.securityService = securityService;
    }

    public void validateShiftConflict(Long employeeId, LocalDate assignmentDate, Long excludeAssignmentId) {

        boolean hasConflict = shiftAssignmentRepository.existsConflictExcludingAssignment(
                employeeId, assignmentDate, excludeAssignmentId
        );
        if (hasConflict) {
            throw new ShiftConflictException("Employee already has a shift scheduled on " + assignmentDate);
        }

        boolean isOnLeave = leaveRequestRepository.isEmployeeOnApprovedLeave(
                employeeId, "APPROVED", assignmentDate
        );
        if (isOnLeave) {
            throw new ShiftConflictException("Employee is on approved leave on " + assignmentDate);
        }
    }

    private List<ShiftAssignmentDTO> mapToDTOList(List<ShiftAssignment> shiftAssignments) {
        return shiftAssignments.stream()
                .map(this::mapToDTO)
                .toList();
    }

    public Page<ShiftAssignmentDTO> fetchAssignmentsForGrid(int offset,int limit,
                                                            LocalDate filterDate,String employeeNameSearch){
        int pageNumber = offset / limit;
        Pageable pageable =
                PageRequest.of(pageNumber, limit, Sort.by("assignmentDate").ascending());

        Page<ShiftAssignment> assignmentPage;

        if (filterDate != null && employeeNameSearch != null && !employeeNameSearch.isBlank()) {
            assignmentPage = shiftAssignmentRepository.findFilteredAssignments(filterDate, employeeNameSearch, pageable);
        }
        else if (filterDate != null) {
            assignmentPage = shiftAssignmentRepository.findByDate(filterDate, pageable);
        }

        else if (employeeNameSearch != null && !employeeNameSearch.isBlank()) {
            assignmentPage = shiftAssignmentRepository.findByEmployeeName(employeeNameSearch, pageable);
        }
        else{
            assignmentPage = shiftAssignmentRepository.findAllAssignments(pageable);
        }

        return assignmentPage.map(this::mapToDTO);
    }

    public List<ShiftAssignmentDTO> fetchAssignmentsForCalendarPivot(LocalDate viewStartDate,LocalDate viewEndDate){
        List<ShiftAssignment> assignments = shiftAssignmentRepository
                .findByAssignmentDateBetween(viewStartDate, viewEndDate);

        assignments = filterOutApprovedFullDayLeaves(assignments);
        return mapToDTOList(assignments);
    }

    public Map<String, Long> getTodayShiftCounts(LocalDate today) {

        List<ShiftAssignment> todayAssignments =
                shiftAssignmentRepository.findByAssignmentDate(today);

        Map<String, Long> shiftCounts = new HashMap<>();

        for (ShiftAssignment assignment : todayAssignments) {
            String shiftName = assignment.getShift().getName();
            shiftCounts.put(shiftName,shiftCounts.getOrDefault(shiftName, 0L) + 1);
        }
        return shiftCounts;
    }

    public Map<String, Map<LocalDate, String>> getWeeklyPivotData(LocalDate startOfWeek,LocalDate endOfWeek){

        List<ShiftAssignment> weeklyAssignments =
                shiftAssignmentRepository.findByAssignmentDateBetween(startOfWeek,endOfWeek);

        weeklyAssignments = filterOutApprovedFullDayLeaves(weeklyAssignments);

        Map<String, Map<LocalDate, String>> pivotData = new HashMap<>();
        for (ShiftAssignment assignment : weeklyAssignments) {
            String employeeName =assignment.getEmployee().getFirstName();
            LocalDate assignmentDate =assignment.getAssignmentDate();
            String shiftName =assignment.getShift().getName();

            Map<LocalDate, String> employeeSchedule =pivotData.getOrDefault(employeeName,new HashMap<>());

            employeeSchedule.putIfAbsent(assignmentDate,shiftName);

            pivotData.put(employeeName, employeeSchedule);
        }
        return pivotData;
    }

    public BatchPreviewResponse previewBatchAssignments(List<Long> employeeIds, Long shiftId, LocalDate startDate, String duration) {
        LocalDate endDate = calculateEndDate(startDate, duration);
        return previewBatchAssignments(employeeIds, shiftId, startDate, endDate);
    }

    public BatchPreviewResponse previewBatchAssignments(List<Long> employeeIds, Long shiftId
            , LocalDate startDate, LocalDate endDate) {
        BatchPreviewResponse response = new BatchPreviewResponse();

        // 2. Fetch base entities directly (Throws exception immediately if missing to avoid Optional wrappers)
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ShiftNotFoundException("Shift not found with ID: " + shiftId));

        Map<Long, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, emp -> emp));

        // 3. Pre-fetch blockouts and existing assignments (Bulk Fetching)
        Set<LocalDate> holidayDates =
                new HashSet<>(holidayRepository.findHolidayDatesBetween(startDate, endDate));

        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(
                employeeIds, "APPROVED", startDate, endDate);

        // Group leaves by Employee ID for fast lookup
        Map<Long, List<LeaveRequest>> leavesByEmployee = leaves.stream()
                .collect(Collectors.groupingBy(l -> l.getEmployee().getId()));

        List<ShiftAssignment> existingAssignments = shiftAssignmentRepository
                .findByEmployeeIdInAndAssignmentDateBetween(employeeIds, startDate, endDate);

        // Map assignments using a composite key: EmployeeId_Date
        Map<String, ShiftAssignment> assignmentsMap = existingAssignments.stream()
                .collect(Collectors.toMap(a -> a.getEmployee().getId() + "_" + a.getAssignmentDate(), a -> a));

        boolean isSingleDay = startDate.isEqual(endDate);

        // 4. The Validation Loop
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {

            boolean isHoliday = holidayDates.contains(currentDate);

            String currentDayName = currentDate.getDayOfWeek().name();
            boolean isShiftWorkingDay = shift.getWorkingDays().stream()
                    .anyMatch(wd -> wd.name().equals(currentDayName));

            for (Long empId : employeeIds) {
                Employee employee = employeeMap.get(empId);
                if (employee == null) continue;

                String mapKey = empId + "_" + currentDate;
                boolean hasExistingShift = assignmentsMap.containsKey(mapKey);

                List<LeaveRequest> empLeaves = leavesByEmployee.getOrDefault(empId, Collections.emptyList());
                LeaveRequest activeLeave = getActiveLeaveForDate(empLeaves, currentDate);

                boolean isInvalidDay = !isShiftWorkingDay && !isSingleDay;

                if (hasExistingShift || isHoliday || isInvalidDay || (activeLeave != null && isFullDayLeave(activeLeave))){

                    // --- HARD CONFLICT ---
                    ShiftConflictDTO conflict = createConflictBase(employee, shift, currentDate);

                    if (hasExistingShift) conflict.setConflictType("Overlap");
                    else if (isHoliday) conflict.setConflictType("Holiday");
                    else if (isInvalidDay) conflict.setConflictType("Non-Working Day");
                    else conflict.setConflictType("Full Leave");

                    response.getHardConflicts().add(conflict);

                } else if (activeLeave != null && isHalfDayLeave(activeLeave)) {

                    // --- PARTIAL CONFLICT (Half-Day) ---
                    ShiftConflictDTO conflict = createConflictBase(employee, shift, currentDate);
                    conflict.setConflictType("Half-Day Leave");
                    conflict.setStandardStartTime(shift.getStartTime());
                    conflict.setStandardEndTime(shift.getEndTime());

                    LocalTime midPoint = calculateShiftMidPoint(shift.getStartTime(), shift.getEndTime());

                    if (LeaveSession.SECOND_HALF.equals(activeLeave.getLeaveSession())) {
                        // Taking the afternoon off. They work the morning.
                        conflict.setSystemResolution("Work: " + shift.getStartTime() + " to " + midPoint + " | Leave: 2nd Half");
                        conflict.setSuggestedOverrideStart(shift.getStartTime());
                        conflict.setSuggestedOverrideEnd(midPoint);
                    } else {
                        // Default to FIRST_HALF (Taking the morning off. They work the afternoon).
                        conflict.setSystemResolution("Leave: 1st Half | Work: " + midPoint + " to " + shift.getEndTime());
                        conflict.setSuggestedOverrideStart(midPoint);
                        conflict.setSuggestedOverrideEnd(shift.getEndTime());
                    }

                    response.getPartialConflicts().add(conflict);

                } else{

                    ShiftAssignmentDTO dto = new ShiftAssignmentDTO();
                    dto.setEmployeeId(empId);
                    dto.setEmployeeName(employee.getFirstName());
                    dto.setShiftId(shiftId);
                    dto.setShiftName(shift.getName());
                    dto.setShiftType(shift.getShiftType());
                    dto.setAssignmentDate(currentDate);

                    response.getReadyToSave().add(dto);
                }
            }
            currentDate = currentDate.plusDays(1);
        }
        System.out.println("START: " + startDate);
        System.out.println("END: " + endDate);
        System.out.println("HOLIDAYS: " + holidayDates);
        return response;
    }

    @Transactional
    public void saveResolvedBatch(List<ShiftAssignmentDTO> finalCleanAssignments) {
        if (finalCleanAssignments == null || finalCleanAssignments.isEmpty()) {
            return;
        }

        // Extract unique IDs for bulk fetching
        Set<Long> employeeIds = finalCleanAssignments.stream().map(ShiftAssignmentDTO::getEmployeeId).collect(Collectors.toSet());
        Set<Long> shiftIds = finalCleanAssignments.stream().map(ShiftAssignmentDTO::getShiftId).collect(Collectors.toSet());

        Map<Long, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, emp -> emp));
        Map<Long, Shift> shiftMap = shiftRepository.findAllById(shiftIds).stream()
                .collect(Collectors.toMap(Shift::getId, shift -> shift));

        List<ShiftAssignment> assignmentsToSave = new ArrayList<>();

        for (ShiftAssignmentDTO dto : finalCleanAssignments) {

            // Validation: Ensure both override times are provided if one is present
            if ((dto.getOverrideStartTime() == null) != (dto.getOverrideEndTime() == null)) {
                throw new IllegalArgumentException("Both override times must be provided for Employee ID: "
                        + dto.getEmployeeId() + " on " + dto.getAssignmentDate());
            }

            Employee employee = employeeMap.get(dto.getEmployeeId());
            Shift shift = shiftMap.get(dto.getShiftId());

            if (employee != null && shift != null) {
                ShiftAssignment assignment = new ShiftAssignment();
                assignment.setEmployee(employee);
                assignment.setShift(shift);
                assignment.setAssignmentDate(dto.getAssignmentDate());

                // Apply overrides if user adjusted a partial conflict
                if (dto.getOverrideStartTime() != null && dto.getOverrideEndTime() != null) {
                    assignment.setOverrideStartTime(dto.getOverrideStartTime());
                    assignment.setOverrideEndTime(dto.getOverrideEndTime());
                    assignment.setOverrideApplied(true);
                }

                assignmentsToSave.add(assignment);
            }
        }

        shiftAssignmentRepository.saveAll(assignmentsToSave);

        log.info("Batch of {} shift assignments saved successfully.", assignmentsToSave.size());
        saveAuditLog(null, "BATCH_CREATED", "shift_assignments", "Created " + assignmentsToSave.size() + " shift assignments.");

        LocalDate minDate = assignmentsToSave.stream().map(ShiftAssignment::getAssignmentDate).min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate maxDate = assignmentsToSave.stream().map(ShiftAssignment::getAssignmentDate).max(LocalDate::compareTo).orElse(LocalDate.now());
        List<Long> savedEmpIds = assignmentsToSave.stream().map(a -> a.getEmployee().getId()).distinct().toList();

        List<LeaveRequest> existingLeaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(
                savedEmpIds, "APPROVED", minDate, maxDate);

        for (LeaveRequest leave : existingLeaves) {
            attendanceSyncService.syncLeaveRecords(leave);
        }
    }

    @Transactional
    public ShiftAssignmentDTO updateSingleAssignment(ShiftAssignmentDTO assignmentDTO) {

        // 1. Validate Overrides (Business logic regarding time inputs)
        if ((assignmentDTO.getOverrideStartTime() == null) != (assignmentDTO.getOverrideEndTime() == null)) {
            throw new IllegalArgumentException("Both override times must be provided, or both must be null.");
        }

        // 2. Context-Aware Conflict Validation
        // We pass the ID so the engine ignores the very record we are editing
        validateShiftConflict(
                assignmentDTO.getEmployeeId(),
                assignmentDTO.getAssignmentDate(),
                assignmentDTO.getId()
        );

        // 3. Fetch existing record
        ShiftAssignment existingAssignment = shiftAssignmentRepository.findById(assignmentDTO.getId())
                .orElseThrow(() -> new EntityNotFoundException("Shift Assignment not found with ID: " + assignmentDTO.getId()));

        // 4. Update Shift template if changed
        if (!existingAssignment.getShift().getId().equals(assignmentDTO.getShiftId())) {
            Shift newShift = shiftRepository.findById(assignmentDTO.getShiftId())
                    .orElseThrow(() -> new ShiftNotFoundException("Shift not found with ID: " + assignmentDTO.getShiftId()));
            existingAssignment.setShift(newShift);
        }

        // 5. Update remaining fields
        existingAssignment.setAssignmentDate(assignmentDTO.getAssignmentDate());
        existingAssignment.setOverrideStartTime(assignmentDTO.getOverrideStartTime());
        existingAssignment.setOverrideEndTime(assignmentDTO.getOverrideEndTime());
        existingAssignment.setOverrideApplied(assignmentDTO.getOverrideStartTime() != null);

        // 6. Save and Map
        ShiftAssignment savedAssignment = shiftAssignmentRepository.save(existingAssignment);

        log.info("Shift assignment UPDATED successfully. ID: {}", savedAssignment.getId());
        saveAuditLog(savedAssignment.getId(), "UPDATED", "shift_assignments", "Updated shift assignment for employee ID " + savedAssignment.getEmployee().getId() + " on " + savedAssignment.getAssignmentDate());

        List<LeaveRequest> existingLeaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(
                List.of(savedAssignment.getEmployee().getId()),
                "APPROVED",
                savedAssignment.getAssignmentDate(),
                savedAssignment.getAssignmentDate());

        for (LeaveRequest leave : existingLeaves) {
            attendanceSyncService.syncLeaveRecords(leave);
        }

        return mapToDTO(savedAssignment);
    }

    @Transactional
    public void deleteAssignment(Long id) {
        if (!shiftAssignmentRepository.existsById(id)) {
            throw new ShiftNotFoundException("Cannot delete: Shift Assignment not found with ID: " + id);
        }
        shiftAssignmentRepository.deleteById(id);

        log.info("Shift assignment DELETED successfully. ID: {}", id);
        saveAuditLog(id, "DELETED", "shift_assignments", "Deleted shift assignment ID: " + id);
    }

    @Transactional
    public void applyHalfDayLeaveOverride(LeaveRequest leaveRequest) {
        // Only process if it's actually a half-day leave
        if (leaveRequest.getDurationDays().compareTo(new BigDecimal("0.5")) != 0) {
            return;
        }

        // Fetch the shift assignment for this specific day
        shiftAssignmentRepository.findByEmployeeIdAndAssignmentDate(
                leaveRequest.getEmployee().getId(), leaveRequest.getStartDate()
        ).ifPresent(assignment -> {

            Shift shift = assignment.getShift();
            LocalTime start = shift.getStartTime();
            LocalTime end = shift.getEndTime();

            // Calculate total duration safely, accounting for night shifts that cross midnight
            long durationMinutes;
            if (start.isBefore(end) || start.equals(end)) {
                durationMinutes = java.time.Duration.between(start, end).toMinutes();
            } else {
                // Night shift math: Time until midnight + Time from midnight to end
                durationMinutes = java.time.Duration.between(start, LocalTime.MAX).toMinutes() + 1 +
                        java.time.Duration.between(LocalTime.MIN, end).toMinutes();
            }

            LocalTime midPoint = calculateShiftMidPoint(start, end);

            // Apply overrides based on which half they took off
            if (LeaveSession.FIRST_HALF.equals(leaveRequest.getLeaveSession())) {
                // Took morning off. They work the second half.
                assignment.setOverrideStartTime(midPoint);
                assignment.setOverrideEndTime(end);
            } else if (LeaveSession.SECOND_HALF.equals(leaveRequest.getLeaveSession())) {
                // Took afternoon off. They work the first half.
                assignment.setOverrideStartTime(start);
                assignment.setOverrideEndTime(midPoint);
            }

            assignment.setOverrideApplied(true);
            shiftAssignmentRepository.save(assignment);

            log.info("Applied Half-Day Override for Employee {} on {}. New working hours: {} to {}",
                    leaveRequest.getEmployee().getId(), leaveRequest.getStartDate(),
                    assignment.getOverrideStartTime(), assignment.getOverrideEndTime());
        });
    }

    @Transactional
    public void revertHalfDayLeaveOverride(LeaveRequest leaveRequest) {
        if (leaveRequest.getDurationDays().compareTo(new BigDecimal("0.5")) != 0) {
            return;
        }

        shiftAssignmentRepository.findByEmployeeIdAndAssignmentDate(
                leaveRequest.getEmployee().getId(), leaveRequest.getStartDate()
        ).ifPresent(assignment -> {

            // Revert back to the standard shift template
            assignment.setOverrideStartTime(null);
            assignment.setOverrideEndTime(null);
            assignment.setOverrideApplied(false);

            shiftAssignmentRepository.save(assignment);

            log.info("Reverted Half-Day Override for Employee {} on {}. Back to standard shift.",
                    leaveRequest.getEmployee().getId(), leaveRequest.getStartDate());
        });
    }


    private LocalDate calculateEndDate(LocalDate startDate, String duration) {
        return switch (duration.toLowerCase()) {
            case "1 week" -> startDate.plusWeeks(1).minusDays(1);
            case "2 weeks" -> startDate.plusWeeks(2).minusDays(1);
            case "1 month" -> startDate.plusMonths(1).minusDays(1);
            case "3 months" -> startDate.plusMonths(3).minusDays(1);
            case "6 months" -> startDate.plusMonths(6).minusDays(1);
            default -> startDate.plusWeeks(1).minusDays(1);
        };
    }

    private LeaveRequest getActiveLeaveForDate(List<LeaveRequest> leaves, LocalDate targetDate) {
        for (LeaveRequest leave : leaves) {
            if (!targetDate.isBefore(leave.getStartDate()) && !targetDate.isAfter(leave.getEndDate())) {
                return leave;
            }
        }
        return null;
    }

    private boolean isHalfDayLeave(LeaveRequest leave) {
        // Assuming duration is stored exactly as 0.5 for half days
        return leave.getDurationDays().compareTo(new BigDecimal("0.5")) == 0;
    }

    private boolean isFullDayLeave(LeaveRequest leave) {
        if (leave == null) return false;
        return leave.getDurationDays().compareTo(BigDecimal.valueOf(1.0)) >= 0;
    }

    private ShiftConflictDTO createConflictBase(Employee employee, Shift shift, LocalDate date) {
        ShiftConflictDTO dto = new ShiftConflictDTO();
        dto.setEmployeeId(employee.getId());
        dto.setEmployeeName(employee.getFirstName());
        dto.setShiftId(shift.getId());
        dto.setShiftName(shift.getName());
        dto.setConflictDate(date);
        return dto;
    }

    private ShiftAssignmentDTO mapToDTO(ShiftAssignment assignment) {
        ShiftAssignmentDTO dto = new ShiftAssignmentDTO();

        dto.setId(assignment.getId());
        dto.setEmployeeId(assignment.getEmployee().getId());
        dto.setEmployeeName(assignment.getEmployee().getFirstName());
        dto.setShiftId(assignment.getShift().getId());
        dto.setShiftName(assignment.getShift().getName());
        dto.setShiftType(assignment.getShift().getShiftType());
        dto.setAssignmentDate(assignment.getAssignmentDate());

        dto.setStartTime(assignment.getEffectiveStartTime());
        dto.setEndTime(assignment.getEffectiveEndTime());
        dto.setIsOverride(assignment.getOverrideApplied());
        dto.setOverrideStartTime(assignment.getOverrideStartTime());
        dto.setOverrideEndTime(assignment.getOverrideEndTime());

        return dto;
    }

    private List<ShiftAssignment> filterOutApprovedFullDayLeaves(List<ShiftAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) return assignments;

        LocalDate minDate = assignments.stream().map(ShiftAssignment::getAssignmentDate).min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate maxDate = assignments.stream().map(ShiftAssignment::getAssignmentDate).max(LocalDate::compareTo).orElse(LocalDate.now());
        List<Long> empIds = assignments.stream().map(a -> a.getEmployee().getId()).distinct().toList();

        // Fetch all approved leaves for these employees in this date range
        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(
                empIds, "APPROVED", minDate, maxDate);

        // Group by employee for fast lookup
        Map<Long, List<LeaveRequest>> leavesByEmployee = leaves.stream()
                .collect(Collectors.groupingBy(l -> l.getEmployee().getId()));

        return assignments.stream().filter(assignment -> {
            List<LeaveRequest> empLeaves = leavesByEmployee.getOrDefault(assignment.getEmployee().getId(), Collections.emptyList());
            LeaveRequest activeLeave = getActiveLeaveForDate(empLeaves, assignment.getAssignmentDate());

            if (activeLeave != null) {
                boolean isFullDay = isFullDayLeave(activeLeave);
                log.info("Date: {} | Leave ID: {} | Detected as Full Day? {}",
                        assignment.getAssignmentDate(), activeLeave.getId(), isFullDay);

                // If it IS a full day leave, return false to filter it out.
                // If it's a half day, return true to keep it.
                return !isFullDay;
            }

            // No leave active, keep the shift visible
            return true;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ShiftAssignmentDTO> getTeamUpcomingShifts(Long managerId, LocalDate startDate, LocalDate endDate) {
        // 1. Get reporting employees
        List<Employee> reportingEmployees = employeeRepository.findReportingEmployees(managerId);
        if (reportingEmployees.isEmpty()) {
            return List.of();
        }

        List<Long> teamIds = reportingEmployees.stream().map(Employee::getId).toList();

        // 2. Fetch assignments for the team in the date range
        List<ShiftAssignment> teamAssignments = shiftAssignmentRepository
                .findByEmployeeIdInAndAssignmentDateBetween(teamIds, startDate, endDate);

        // 3. Filter out full-day leaves and map to DTO
        teamAssignments = filterOutApprovedFullDayLeaves(teamAssignments);
        return mapToDTOList(teamAssignments);
    }

    private LocalTime calculateShiftMidPoint(LocalTime start, LocalTime end) {
        long durationMinutes;
        if (start.isBefore(end) || start.equals(end)) {
            durationMinutes = java.time.Duration.between(start, end).toMinutes();
        } else {
            durationMinutes = java.time.Duration.between(start, LocalTime.MAX).toMinutes() + 1 +
                    java.time.Duration.between(LocalTime.MIN, end).toMinutes();
        }
        return start.plusMinutes(durationMinutes / 2);
    }

    // --- ADDED HELPER METHOD ---
    private void saveAuditLog(Long recordId, String action, String tableAffected, String details) {
        try {
            String username = "SYSTEM";
            String role = "SYSTEM";

            if (securityService.getPrincipal() != null) {
                username = securityService.getPrincipal().getUsername();
                if (securityService.getAuthentication() != null && !securityService.getAuthentication().getAuthorities().isEmpty()) {
                    role = securityService.getAuthentication().getAuthorities().iterator().next().getAuthority();
                }
            }

            AuditLog auditLog = new AuditLog();
            auditLog.setUsername(username);
            auditLog.setRole(role);
            auditLog.setRecordId(recordId);
            auditLog.setAction(action);
            auditLog.setTableAffected(tableAffected);
            auditLog.setDetails(details);

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log for shift assignment record {}: {}", recordId, e.getMessage());
        }
    }
}