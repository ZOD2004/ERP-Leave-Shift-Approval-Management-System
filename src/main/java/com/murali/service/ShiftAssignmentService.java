package com.murali.service;

import com.murali.dto.BatchPreviewResponse;
import com.murali.dto.ShiftAssignmentDTO;
import com.murali.dto.ShiftConflictDTO;
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
    private final AuditLogService auditLoggingService;

    public ShiftAssignmentService(ShiftAssignmentRepository shiftAssignmentRepository,
                                  HolidayRepository holidayRepository,
                                  LeaveRequestRepository leaveRequestRepository,
                                  EmployeeRepository employeeRepository,
                                  ShiftRepository shiftRepository, AttendanceSyncService attendanceSyncService,
                                  AuditLogService auditLoggingService) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.holidayRepository = holidayRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
        this.attendanceSyncService = attendanceSyncService;
        this.auditLoggingService = auditLoggingService;
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

    public List<ShiftAssignmentDTO> fetchAssignmentsForCalendar(LocalDate viewStartDate,LocalDate viewEndDate){
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

    public BatchPreviewResponse previewBatchAssignments(List<Long> employeeIds, Long shiftId, LocalDate startDate, String duration) {
        LocalDate endDate = calculateEndDate(startDate, duration);
        return previewBatchAssignments(employeeIds, shiftId, startDate, endDate);
    }

    public BatchPreviewResponse previewBatchAssignments(List<Long> employeeIds, Long shiftId
            , LocalDate startDate, LocalDate endDate) {
        BatchPreviewResponse response = new BatchPreviewResponse();

        // the created shift not shiftAssignmentId
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ShiftNotFoundException("Shift not found with ID: " + shiftId));

        Map<Long, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, emp -> emp));

        Set<LocalDate> holidayDates =
                new HashSet<>(holidayRepository.findHolidayDatesBetween(startDate, endDate));

        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(
                employeeIds, "APPROVED", startDate, endDate);

        Map<Long, List<LeaveRequest>> leavesByEmployee = leaves.stream()
                .collect(Collectors.groupingBy(l -> l.getEmployee().getId()));

        List<ShiftAssignment> existingAssignments = shiftAssignmentRepository
                .findByEmployeeIdInAndAssignmentDateBetween(employeeIds, startDate, endDate);

        Map<String, ShiftAssignment> assignmentsMap = existingAssignments.stream()
                .collect(Collectors.toMap(a -> a.getEmployee().getId() + "_" + a.getAssignmentDate(), a -> a));

        boolean isSingleDay = startDate.isEqual(endDate);

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

                    ShiftConflictDTO conflict = createConflictBase(employee, shift, currentDate);

                    if (hasExistingShift) conflict.setConflictType("Overlap");
                    else if (isHoliday) conflict.setConflictType("Holiday");
                    else if (isInvalidDay) conflict.setConflictType("Non-Working Day");
                    else conflict.setConflictType("Full Leave");

                    response.getHardConflicts().add(conflict);

                } else if (activeLeave != null && isHalfDayLeave(activeLeave)) {

                    ShiftConflictDTO conflict = createConflictBase(employee, shift, currentDate);
                    conflict.setConflictType("Half-Day Leave");
                    conflict.setStandardStartTime(shift.getStartTime());
                    conflict.setStandardEndTime(shift.getEndTime());

                    LocalTime midPoint = calculateShiftMidPoint(shift.getStartTime(), shift.getEndTime());

                    if (LeaveSession.SECOND_HALF.equals(activeLeave.getLeaveSession())) {
                        conflict.setSystemResolution("Work: " + shift.getStartTime() + " to " + midPoint + " | Leave: 2nd Half");
                        conflict.setSuggestedOverrideStart(shift.getStartTime());
                        conflict.setSuggestedOverrideEnd(midPoint);
                    } else {
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

        Set<Long> employeeIds = finalCleanAssignments.stream().map(ShiftAssignmentDTO::getEmployeeId).collect(Collectors.toSet());
        Set<Long> shiftIds = finalCleanAssignments.stream().map(ShiftAssignmentDTO::getShiftId).collect(Collectors.toSet());

        Map<Long, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, emp -> emp));
        Map<Long, Shift> shiftMap = shiftRepository.findAllById(shiftIds).stream()
                .collect(Collectors.toMap(Shift::getId, shift -> shift));

        List<ShiftAssignment> assignmentsToSave = new ArrayList<>();

        for (ShiftAssignmentDTO dto : finalCleanAssignments) {

            //true when only one side differs
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

        String newState = String.format("{ \"batchSize\": %d }", assignmentsToSave.size());
        auditLoggingService.saveAuditLog(null, "BATCH_CREATED", "shift_assignments", null, newState);

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
    public void updateSingleAssignment(ShiftAssignmentDTO assignmentDTO) {

        if ((assignmentDTO.getOverrideStartTime() == null) != (assignmentDTO.getOverrideEndTime() == null)) {
            throw new IllegalArgumentException("Both override times must be provided, or both must be null.");
        }

        validateShiftConflict(
                assignmentDTO.getEmployeeId(),
                assignmentDTO.getAssignmentDate(),
                assignmentDTO.getId()
        );

        ShiftAssignment existingAssignment = shiftAssignmentRepository.findById(assignmentDTO.getId())
                .orElseThrow(() -> new EntityNotFoundException("Shift Assignment not found with ID: " + assignmentDTO.getId()));

        String oldState = String.format("{ \"shiftId\": %d, \"assignmentDate\": \"%s\", \"overrideStartTime\": \"%s\", \"overrideEndTime\": \"%s\", \"isOverride\": %b }",
                existingAssignment.getShift().getId(), existingAssignment.getAssignmentDate(),
                existingAssignment.getOverrideStartTime(), existingAssignment.getOverrideEndTime(),
                existingAssignment.getOverrideApplied());

        if (!existingAssignment.getShift().getId().equals(assignmentDTO.getShiftId())) {
            Shift newShift = shiftRepository.findById(assignmentDTO.getShiftId())
                    .orElseThrow(() -> new ShiftNotFoundException("Shift not found with ID: " + assignmentDTO.getShiftId()));
            existingAssignment.setShift(newShift);
        }

        existingAssignment.setAssignmentDate(assignmentDTO.getAssignmentDate());
        existingAssignment.setOverrideStartTime(assignmentDTO.getOverrideStartTime());
        existingAssignment.setOverrideEndTime(assignmentDTO.getOverrideEndTime());
        existingAssignment.setOverrideApplied(assignmentDTO.getOverrideStartTime() != null);

        ShiftAssignment savedAssignment = shiftAssignmentRepository.save(existingAssignment);

        String newState = String.format("{ \"shiftId\": %d, \"assignmentDate\": \"%s\", \"overrideStartTime\": \"%s\", \"overrideEndTime\": \"%s\", \"isOverride\": %b }",
                savedAssignment.getShift().getId(), savedAssignment.getAssignmentDate(),
                savedAssignment.getOverrideStartTime(), savedAssignment.getOverrideEndTime(),
                savedAssignment.getOverrideApplied());

        log.info("Shift assignment UPDATED successfully. ID: {}", savedAssignment.getId());
        auditLoggingService.saveAuditLog(savedAssignment.getId(), "UPDATED", "shift_assignments", oldState, newState);

        List<LeaveRequest> existingLeaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(
                List.of(savedAssignment.getEmployee().getId()),
                "APPROVED",
                savedAssignment.getAssignmentDate(),
                savedAssignment.getAssignmentDate());

        for (LeaveRequest leave : existingLeaves) {
            attendanceSyncService.syncLeaveRecords(leave);
        }

//        return mapToDTO(savedAssignment);
    }

    @Transactional
    public void deleteAssignment(Long id) {
        ShiftAssignment existingAssignment = shiftAssignmentRepository.findById(id)
                .orElseThrow(() -> new ShiftNotFoundException("Cannot delete: Shift Assignment not found with ID: " + id));

        String oldState = String.format("{ \"shiftId\": %d, \"assignmentDate\": \"%s\" }",
                existingAssignment.getShift().getId(), existingAssignment.getAssignmentDate());

        List<LeaveRequest> existingLeaves =
                leaveRequestRepository.findApprovedLeavesForEmployeesInRange(
                        List.of(existingAssignment.getEmployee().getId()),
                        "APPROVED",
                        existingAssignment.getAssignmentDate(),
                        existingAssignment.getAssignmentDate()
                );

        for (LeaveRequest leave : existingLeaves) {
            attendanceSyncService.revertLeaveFromAttendance(leave);
        }


        shiftAssignmentRepository.deleteById(id);

        log.info("Shift assignment DELETED successfully. ID: {}", id);
        auditLoggingService.saveAuditLog(id, "DELETED", "shift_assignments", oldState, null);
    }

    @Transactional
    public void applyHalfDayLeaveOverride(LeaveRequest leaveRequest) {
        if (leaveRequest.getDurationDays().compareTo(new BigDecimal("0.5")) != 0) {
            return;
        }

        shiftAssignmentRepository.findByEmployeeIdAndAssignmentDate(
                leaveRequest.getEmployee().getId(), leaveRequest.getStartDate()
        ).ifPresent(assignment -> {

            String oldState = String.format("{ \"overrideStartTime\": \"%s\", \"overrideEndTime\": \"%s\", \"isOverride\": %b }",
                    assignment.getOverrideStartTime(), assignment.getOverrideEndTime(), assignment.getOverrideApplied());

            Shift shift = assignment.getShift();
            LocalTime start = shift.getStartTime();
            LocalTime end = shift.getEndTime();

            LocalTime midPoint = calculateShiftMidPoint(start, end);

            if (LeaveSession.FIRST_HALF.equals(leaveRequest.getLeaveSession())) {
                assignment.setOverrideStartTime(midPoint);
                assignment.setOverrideEndTime(end);
            } else if (LeaveSession.SECOND_HALF.equals(leaveRequest.getLeaveSession())) {
                assignment.setOverrideStartTime(start);
                assignment.setOverrideEndTime(midPoint);
            }

            assignment.setOverrideApplied(true);
            ShiftAssignment savedAssignment = shiftAssignmentRepository.save(assignment);

            String newState = String.format("{ \"overrideStartTime\": \"%s\", \"overrideEndTime\": \"%s\", \"isOverride\": %b }",
                    savedAssignment.getOverrideStartTime(), savedAssignment.getOverrideEndTime(), savedAssignment.getOverrideApplied());

            log.info("Applied Half-Day Override for Employee {} on {}. New working hours: {} to {}",
                    leaveRequest.getEmployee().getId(), leaveRequest.getStartDate(),
                    savedAssignment.getOverrideStartTime(), savedAssignment.getOverrideEndTime());

            auditLoggingService.saveAuditLog(savedAssignment.getId(), "UPDATED", "shift_assignments", oldState, newState);
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

            String oldState = String.format("{ \"overrideStartTime\": \"%s\", \"overrideEndTime\": \"%s\", \"isOverride\": %b }",
                    assignment.getOverrideStartTime(), assignment.getOverrideEndTime(), assignment.getOverrideApplied());

            assignment.setOverrideStartTime(null);
            assignment.setOverrideEndTime(null);
            assignment.setOverrideApplied(false);

            ShiftAssignment savedAssignment = shiftAssignmentRepository.save(assignment);

            String newState = String.format("{ \"overrideStartTime\": \"%s\", \"overrideEndTime\": \"%s\", \"isOverride\": %b }",
                    savedAssignment.getOverrideStartTime(), savedAssignment.getOverrideEndTime(), savedAssignment.getOverrideApplied());

            log.info("Reverted Half-Day Override for Employee {} on {}. Back to standard shift.",
                    leaveRequest.getEmployee().getId(), leaveRequest.getStartDate());

            auditLoggingService.saveAuditLog(savedAssignment.getId(), "UPDATED", "shift_assignments", oldState, newState);
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

        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(
                empIds, "APPROVED", minDate, maxDate);

        Map<Long, List<LeaveRequest>> leavesByEmployee = new HashMap<>();
        for (LeaveRequest leave : leaves) {
            Long empId = leave.getEmployee().getId();
            if (!leavesByEmployee.containsKey(empId)) {
                leavesByEmployee.put(empId, new ArrayList<>());
            }
            leavesByEmployee.get(empId).add(leave);
        }

        return assignments.stream().filter(assignment -> {
            List<LeaveRequest> empLeaves = leavesByEmployee.getOrDefault(assignment.getEmployee().getId(), Collections.emptyList());
            LeaveRequest activeLeave = getActiveLeaveForDate(empLeaves, assignment.getAssignmentDate());

            if (activeLeave != null) {
                boolean isFullDay = isFullDayLeave(activeLeave);
                log.info("Date: {} | Leave ID: {} | Detected as Full Day? {}",
                        assignment.getAssignmentDate(), activeLeave.getId(), isFullDay);

                return !isFullDay;
            }

            return true;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ShiftAssignmentDTO> getTeamUpcomingShifts(Long managerId, LocalDate startDate, LocalDate endDate) {
        List<Employee> reportingEmployees = employeeRepository.findReportingEmployees(managerId);
        if (reportingEmployees.isEmpty()) {
            return List.of();
        }

        List<Long> teamIds = reportingEmployees.stream().map(Employee::getId).toList();

        List<ShiftAssignment> teamAssignments = shiftAssignmentRepository
                .findByEmployeeIdInAndAssignmentDateBetween(teamIds, startDate, endDate);

        teamAssignments = filterOutApprovedFullDayLeaves(teamAssignments);
        return mapToDTOList(teamAssignments);
    }

    private LocalTime calculateShiftMidPoint(LocalTime start, LocalTime end) {
        long durationMinutes;
        if (start.isBefore(end) || start.equals(end)) {
            durationMinutes = java.time.Duration.between(start, end).toMinutes();
        } else {
            //night shift
            durationMinutes = java.time.Duration.between(start, LocalTime.MAX).toMinutes() + 1 +
                    java.time.Duration.between(LocalTime.MIN, end).toMinutes();
        }
        return start.plusMinutes(durationMinutes / 2);
    }
}