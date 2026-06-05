package com.murali.service;

import com.murali.dto.BatchPreviewResponse;
import com.murali.dto.DailyCellDTO;
import com.murali.dto.ShiftAssignmentDTO;
import com.murali.dto.ShiftConflictDTO;
import com.murali.entity.*;
import com.murali.entity.enums.LeaveSession;
import com.murali.exception.ShiftConflictException;
import com.murali.exception.ShiftNotFoundException;
import com.murali.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final AttendanceCronJobService attendanceCronJobService;
    private final AuditLogService auditLoggingService;
    private final TimeLogRepository timeLogRepository;

    public ShiftAssignmentService(ShiftAssignmentRepository shiftAssignmentRepository,
                                  HolidayRepository holidayRepository,
                                  LeaveRequestRepository leaveRequestRepository,
                                  EmployeeRepository employeeRepository,
                                  ShiftRepository shiftRepository,
                                  AttendanceCronJobService attendanceCronJobService,
                                  AuditLogService auditLoggingService, TimeLogRepository timeLogRepository) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.holidayRepository = holidayRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
        this.attendanceCronJobService = attendanceCronJobService;
        this.auditLoggingService = auditLoggingService;
        this.timeLogRepository = timeLogRepository;
    }

    // --- 1. PREVIEW & BATCH LOGIC ---

    public BatchPreviewResponse previewBatchAssignments(List<Long> employeeIds, Long shiftId, LocalDate startDate, LocalDate endDate) {
        BatchPreviewResponse response = new BatchPreviewResponse();

        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Shift not found with ID: " + shiftId));

        Map<Long, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, emp -> emp));

        Set<LocalDate> holidayDates = new HashSet<>(holidayRepository.findHolidayDatesBetween(startDate, endDate));

        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(
                employeeIds, "APPROVED", startDate, endDate);

        Map<Long, List<LeaveRequest>> leavesByEmployee = leaves.stream()
                .collect(Collectors.groupingBy(l -> l.getEmployee().getId()));

        List<ShiftAssignment> existingAssignments = shiftAssignmentRepository
                .findByEmployeeIdInAndDateRange(employeeIds, startDate, endDate);

        LocalDate currentDate = startDate;
        Map<Long, List<LocalDate>> validDaysPerEmployee = new HashMap<>();

        while (!currentDate.isAfter(endDate)) {
            boolean isHoliday = holidayDates.contains(currentDate);
            String currentDayName = currentDate.getDayOfWeek().name();

            // Check if today matches the working days defined in the Shift entity
            boolean isShiftWorkingDay = shift.getWorkingDays().stream()
                    .anyMatch(wd -> wd.name().equalsIgnoreCase(currentDayName));

            for (Long empId : employeeIds) {
                Employee employee = employeeMap.get(empId);
                if (employee == null) continue;

                List<LeaveRequest> empLeaves = leavesByEmployee.getOrDefault(empId, Collections.emptyList());
                LeaveRequest activeLeave = getActiveLeaveForDate(empLeaves, currentDate);
                boolean hasExistingShift = isEmployeeAssignedOnDate(existingAssignments, empId, currentDate);
                boolean isInvalidDay = !isShiftWorkingDay;

                // --- THE NEW PRIORITY RESOLUTION ENGINE ---
                if (activeLeave != null || hasExistingShift || isHoliday || isInvalidDay) {

                    ShiftConflictDTO conflict = createConflictBase(employee, shift, currentDate);

                    // 1. LEAVE (Highest Priority - Strict Block)
                    if (activeLeave != null) {
                        LeaveSession session = getSessionForDate(activeLeave, currentDate);
                        if (session == LeaveSession.FULL_DAY) {
                            conflict.setConflictType("Full-Day Leave");
                        } else {
                            conflict.setConflictType("Half-Day Leave (" + session.name() + ")");
                        }
                    }
                    // 2. OVERLAP (Beats Holiday & Off-Days)
                    else if (hasExistingShift) {
                        conflict.setConflictType("Overlap");
                    }
                    // 3. HOLIDAY
                    else if (isHoliday) {
                        conflict.setConflictType("Holiday");
                    }
                    // 4. NON-WORKING DAY
                    else {
                        conflict.setConflictType("Non-Working Day");
                    }

                    response.getHardConflicts().add(conflict);
                } else {
                    // Valid Day! No conflicts found.
                    validDaysPerEmployee.computeIfAbsent(empId, k -> new ArrayList<>()).add(currentDate);
                }
            }
            currentDate = currentDate.plusDays(1);
        }

        // Group the valid days into optimized date ranges
        response.setReadyToSave(groupValidDaysIntoRanges(validDaysPerEmployee, employeeMap, shift));
        return response;
    }

    @Transactional
    public void saveResolvedBatch(List<ShiftAssignmentDTO> finalCleanAssignments, boolean allowOverwrite) {
        if (finalCleanAssignments == null || finalCleanAssignments.isEmpty()) return;

        Set<Long> employeeIds = finalCleanAssignments.stream().map(ShiftAssignmentDTO::getEmployeeId).collect(Collectors.toSet());
        Set<Long> shiftIds = finalCleanAssignments.stream().map(ShiftAssignmentDTO::getShiftId).collect(Collectors.toSet());

        Map<Long, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, emp -> emp));
        Map<Long, Shift> shiftMap = shiftRepository.findAllById(shiftIds).stream()
                .collect(Collectors.toMap(Shift::getId, shift -> shift));

        List<ShiftAssignment> assignmentsToSave = new ArrayList<>();

        for (ShiftAssignmentDTO dto : finalCleanAssignments) {
            Employee employee = employeeMap.get(dto.getEmployeeId());
            Shift shift = shiftMap.get(dto.getShiftId());

            if (employee != null && shift != null) {
                if (allowOverwrite) {
                    punchHoleInExistingShifts(employee.getId(), dto.getStartDate(), dto.getEndDate());
                }

                ShiftAssignment assignment = new ShiftAssignment();
                assignment.setEmployee(employee);
                assignment.setShift(shift);
                assignment.setStartDate(dto.getStartDate());
                assignment.setEndDate(dto.getEndDate());
                assignmentsToSave.add(assignment);
            }
        }

        shiftAssignmentRepository.saveAll(assignmentsToSave);
        log.info("Batch of {} shift assignments saved successfully.", assignmentsToSave.size());
        auditLoggingService.saveAuditLog(null, "BATCH_CREATED", "shift_assignments", null, String.format("{ \"batchSize\": %d }", assignmentsToSave.size()));
    }

    // --- 2. SINGLE ASSIGNMENT UPDATES & HOLE PUNCHING ---

    /**
     * Use this when HR wants to "Hole Punch" an existing range
     * (e.g., Nov 15th changing to Night Shift amidst a month-long Morning shift).
     */
    @Transactional
    public void assignOverrideShift(ShiftAssignmentDTO dto) {
        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        Shift shift = shiftRepository.findById(dto.getShiftId())
                .orElseThrow(() -> new ShiftNotFoundException("Shift not found"));

        // 1. Slice existing overlapping shifts
        punchHoleInExistingShifts(employee.getId(), dto.getStartDate(), dto.getEndDate());

        // 2. Insert new shift in the hole
        ShiftAssignment newAssignment = new ShiftAssignment();
        newAssignment.setEmployee(employee);
        newAssignment.setShift(shift);
        newAssignment.setStartDate(dto.getStartDate());
        newAssignment.setEndDate(dto.getEndDate());

        ShiftAssignment saved = shiftAssignmentRepository.saveAndFlush(newAssignment);
        auditLoggingService.saveAuditLog(saved.getId(), "HOLE_PUNCH_CREATED", "shift_assignments", null,
                String.format("{ \"shiftId\": %d, \"startDate\": \"%s\", \"endDate\": \"%s\" }", shift.getId(), dto.getStartDate(), dto.getEndDate()));

        triggerAttendanceRecalculationIfPast(employee.getId(), dto.getStartDate(), dto.getEndDate());
    }

    /**
     * The core 3-way split logic for overwriting ranges.
     */
    private void punchHoleInExistingShifts(Long employeeId, LocalDate newStart, LocalDate newEnd) {
        List<ShiftAssignment> overlaps = shiftAssignmentRepository.findByEmployeeIdInAndDateRange(
                List.of(employeeId), newStart, newEnd);

        for (ShiftAssignment old : overlaps) {
            LocalDate oldStart = old.getStartDate();
            LocalDate oldEnd = old.getEndDate();

            if (!newStart.isAfter(oldStart) && !newEnd.isBefore(oldEnd)) {
                shiftAssignmentRepository.delete(old);
                shiftAssignmentRepository.flush(); // FIX: Flush immediately after delete
            } else if (newStart.isAfter(oldStart) && newEnd.isBefore(oldEnd)) {
                ShiftAssignment remainder = new ShiftAssignment();
                // ... setters ...
                old.setEndDate(newStart.minusDays(1));
                shiftAssignmentRepository.save(old);
                shiftAssignmentRepository.saveAndFlush(remainder); // FIX: saveAndFlush remainder
            } else if (!newStart.isAfter(oldStart) && newEnd.isBefore(oldEnd)) {
                old.setStartDate(newEnd.plusDays(1));
                shiftAssignmentRepository.saveAndFlush(old); // FIX: saveAndFlush
            } else if (newStart.isAfter(oldStart) && !newEnd.isBefore(oldEnd)) {
                old.setEndDate(newStart.minusDays(1));
                shiftAssignmentRepository.saveAndFlush(old); // FIX: saveAndFlush
            }

            triggerAttendanceRecalculationIfPast(employeeId, oldStart, oldEnd);
        }
    }
    /**
     * Use this when HR wants to change the boundaries or type of an EXISTING row.
     */
    @Transactional
    public void updateSingleAssignment(ShiftAssignmentDTO dto) {
        ShiftAssignment existing = shiftAssignmentRepository.findById(dto.getId())
                .orElseThrow(() -> new EntityNotFoundException("Shift Assignment not found with ID: " + dto.getId()));

        boolean hasConflict = shiftAssignmentRepository.existsConflictExcludingAssignment(
                dto.getEmployeeId(), dto.getStartDate(), dto.getEndDate(), dto.getId());
        if (hasConflict) throw new ShiftConflictException("Cannot update boundaries: Overlaps with another assigned shift.");

        String oldState = String.format("{ \"shiftId\": %d, \"startDate\": \"%s\", \"endDate\": \"%s\" }",
                existing.getShift().getId(), existing.getStartDate(), existing.getEndDate());

        if (!existing.getShift().getId().equals(dto.getShiftId())) {
            Shift newShift = shiftRepository.findById(dto.getShiftId())
                    .orElseThrow(() -> new ShiftNotFoundException("Shift not found with ID: " + dto.getShiftId()));
            existing.setShift(newShift);
        }

        existing.setStartDate(dto.getStartDate());
        existing.setEndDate(dto.getEndDate());
        ShiftAssignment saved = shiftAssignmentRepository.saveAndFlush(existing);

        auditLoggingService.saveAuditLog(saved.getId(), "UPDATED", "shift_assignments", oldState,
                String.format("{ \"shiftId\": %d, \"startDate\": \"%s\", \"endDate\": \"%s\" }",
                        saved.getShift().getId(), saved.getStartDate(), saved.getEndDate()));

        triggerAttendanceRecalculationIfPast(saved.getEmployee().getId(), dto.getStartDate(), dto.getEndDate());
    }

    @Transactional
    public void deleteAssignment(Long id) {
        ShiftAssignment existing = shiftAssignmentRepository.findById(id)
                .orElseThrow(() -> new ShiftNotFoundException("Assignment not found"));

        Long empId = existing.getEmployee().getId();
        LocalDate start = existing.getStartDate();
        LocalDate end = existing.getEndDate();

        shiftAssignmentRepository.deleteById(id);
        auditLoggingService.saveAuditLog(id, "DELETED", "shift_assignments",
                String.format("{ \"shiftId\": %d }", existing.getShift().getId()), null);

        triggerAttendanceRecalculationIfPast(empId, start, end);
    }

    // --- 3. FETCHING & UTILITIES ---

    public Page<ShiftAssignmentDTO> fetchAssignmentsForGrid(int offset, int limit, LocalDate filterDate, String employeeNameSearch){
        Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by("startDate").ascending());
        Page<ShiftAssignment> page;

        if (filterDate != null && employeeNameSearch != null && !employeeNameSearch.isBlank()) {
            page = shiftAssignmentRepository.findFilteredAssignments(filterDate, employeeNameSearch, pageable);
        } else if (filterDate != null) {
            page = shiftAssignmentRepository.findByDate(filterDate, pageable);
        } else if (employeeNameSearch != null && !employeeNameSearch.isBlank()) {
            page = shiftAssignmentRepository.findByEmployeeName(employeeNameSearch, pageable);
        } else {
            page = shiftAssignmentRepository.findAllAssignments(pageable);
        }
        return page.map(this::mapToDTO);
    }

    public List<ShiftAssignmentDTO> fetchAssignmentsForCalendar(LocalDate viewStartDate, LocalDate viewEndDate){
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findOverlappingAssignmentsInRange(viewStartDate, viewEndDate);
        return mapToDTOList(filterOutApprovedFullDayLeaves(assignments));
    }

    @Transactional(readOnly = true)
    public List<ShiftAssignmentDTO> getTeamUpcomingShifts(Long managerId, LocalDate startDate, LocalDate endDate) {
        List<Employee> reporting = employeeRepository.findReportingEmployees(managerId);
        if (reporting.isEmpty()) return List.of();

        List<Long> teamIds = reporting.stream().map(Employee::getId).toList();
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByEmployeeIdInAndDateRange(teamIds, startDate, endDate);
        return mapToDTOList(filterOutApprovedFullDayLeaves(assignments));
    }

    // --- PRIVATE HELPER LOGIC ---

    private List<ShiftAssignmentDTO> groupValidDaysIntoRanges(Map<Long, List<LocalDate>> validDaysPerEmployee, Map<Long, Employee> employeeMap, Shift shift) {
        List<ShiftAssignmentDTO> readyToSave = new ArrayList<>();

        for (Map.Entry<Long, List<LocalDate>> entry : validDaysPerEmployee.entrySet()) {
            Long empId = entry.getKey();
            List<LocalDate> sortedDays = entry.getValue().stream().sorted().toList();

            if (sortedDays.isEmpty()) continue;

            LocalDate currentStart = sortedDays.get(0);
            LocalDate currentEnd = sortedDays.get(0);

            for (int i = 1; i < sortedDays.size(); i++) {
                LocalDate nextDay = sortedDays.get(i);
                if (currentEnd.plusDays(1).equals(nextDay)) {
                    currentEnd = nextDay; // Extend range
                } else {
                    readyToSave.add(createReadyDTO(employeeMap.get(empId), shift, currentStart, currentEnd));
                    currentStart = nextDay;
                    currentEnd = nextDay;
                }
            }
            readyToSave.add(createReadyDTO(employeeMap.get(empId), shift, currentStart, currentEnd));
        }
        return readyToSave;
    }

    private void triggerAttendanceRecalculationIfPast(Long employeeId, LocalDate start, LocalDate end) {
        LocalDate cursor = start;
        LocalDate today = LocalDate.now();
        while (!cursor.isAfter(end) && !cursor.isAfter(today)) {
            attendanceCronJobService.recalculateAttendanceForDate(employeeId, cursor);
            cursor = cursor.plusDays(1);
        }
    }

    private boolean isEmployeeAssignedOnDate(List<ShiftAssignment> existingAssignments, Long empId, LocalDate targetDate) {
        return existingAssignments.stream().anyMatch(a ->
                a.getEmployee().getId().equals(empId) &&
                        !targetDate.isBefore(a.getStartDate()) && !targetDate.isAfter(a.getEndDate()));
    }

    private ShiftAssignmentDTO createReadyDTO(Employee employee, Shift shift, LocalDate start, LocalDate end) {
        ShiftAssignmentDTO dto = new ShiftAssignmentDTO();
        dto.setEmployeeId(employee.getId());
        dto.setEmployeeName(employee.getFirstName());
        dto.setShiftId(shift.getId());
        dto.setShiftName(shift.getName());
        dto.setShiftType(shift.getShiftType());
        dto.setStartDate(start);
        dto.setEndDate(end);
        return dto;
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
        dto.setStartDate(assignment.getStartDate());
        dto.setEndDate(assignment.getEndDate());
        dto.setStartTime(assignment.getShift().getStartTime());
        dto.setEndTime(assignment.getShift().getEndTime());
        return dto;
    }

    private List<ShiftAssignmentDTO> mapToDTOList(List<ShiftAssignment> shiftAssignments) {
        return shiftAssignments.stream().map(this::mapToDTO).toList();
    }

    private LeaveRequest getActiveLeaveForDate(List<LeaveRequest> leaves, LocalDate targetDate) {
        return leaves.stream()
                .filter(l -> !targetDate.isBefore(l.getStartDate()) && !targetDate.isAfter(l.getEndDate()))
                .findFirst().orElse(null);
    }

    private LeaveSession getSessionForDate(LeaveRequest request, LocalDate targetDate) {
        if (request == null) return null;

        // If the leave is just one single day, the startSession dictates the leave type
        if (targetDate.equals(request.getStartDate()) && targetDate.equals(request.getEndDate())) {
            return request.getStartSession();
        }

        // If it's the first day of a multi-day leave
        if (targetDate.equals(request.getStartDate())) {
            return request.getStartSession();
        }

        // If it's the last day of a multi-day leave
        if (targetDate.equals(request.getEndDate())) {
            return request.getEndSession();
        }

        // Any day in the middle of a multi-day leave is ALWAYS a full day
        return LeaveSession.FULL_DAY;
    }

    private boolean isFullDayLeave(LeaveRequest leave, LocalDate date) {
        if (leave == null) return false;
        return getSessionForDate(leave, date) == LeaveSession.FULL_DAY;
    }

    private List<ShiftAssignment> filterOutApprovedFullDayLeaves(List<ShiftAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) return assignments;

        // Uses a massive date range covering the start and end of all assignments to fetch relevant leaves efficiently
        LocalDate minDate = assignments.stream().map(ShiftAssignment::getStartDate).min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate maxDate = assignments.stream().map(ShiftAssignment::getEndDate).max(LocalDate::compareTo).orElse(LocalDate.now());
        List<Long> empIds = assignments.stream().map(a -> a.getEmployee().getId()).distinct().toList();

        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(empIds, "APPROVED", minDate, maxDate);
        Map<Long, List<LeaveRequest>> leavesByEmployee = leaves.stream().collect(Collectors.groupingBy(l -> l.getEmployee().getId()));

        // Note: For date ranges crossing full-day leaves, the frontend calendar will draw the shift,
        // and the leave block will visually layer over the specific full-day leave dates on the UI.
        return assignments;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getTodayShiftCounts(LocalDate date) {
        List<Object[]> counts = shiftAssignmentRepository.countShiftsByDate(date);
        Map<String, Long> stats = new java.util.HashMap<>();
        for (Object[] row : counts) {
            stats.put((String) row[0], (Long) row[1]);
        }
        return stats;
    }
    public List<DailyCellDTO> getResolvedCalendarData(LocalDate startDate, LocalDate endDate) {
        // 1. Fetch active employees (or pass in a specific list if filtering)
        List<Employee> activeEmployees = employeeRepository.findByActiveTrue();
        List<Long> empIds = activeEmployees.stream().map(Employee::getId).toList();

        // 2. Fetch all required data layers
        List<ShiftAssignment> rawAssignments = shiftAssignmentRepository.findByEmployeeIdInAndDateRange(empIds, startDate, endDate);
        Set<LocalDate> holidayDates = new HashSet<>(holidayRepository.findHolidayDatesBetween(startDate, endDate));
        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(empIds, "APPROVED", startDate, endDate);

        // 3. Group for fast processing
        Map<Long, List<ShiftAssignment>> assignmentsByEmp = rawAssignments.stream()
                .collect(Collectors.groupingBy(a -> a.getEmployee().getId()));
        Map<Long, List<LeaveRequest>> leavesByEmp = leaves.stream()
                .collect(Collectors.groupingBy(l -> l.getEmployee().getId()));

        List<DailyCellDTO> resolvedData = new ArrayList<>();

        // 4. Resolve Day-by-Day
        for (Employee emp : activeEmployees) {
            List<ShiftAssignment> empAssignments = assignmentsByEmp.getOrDefault(emp.getId(), List.of());
            List<LeaveRequest> empLeaves = leavesByEmp.getOrDefault(emp.getId(), List.of());

            LocalDate cursor = startDate;
            while (!cursor.isAfter(endDate)) {
                DailyCellDTO cell = new DailyCellDTO();
                cell.setDate(cursor);
                cell.setEmployeeId(emp.getId());
                cell.setEmployeeName(emp.getFirstName());

                // A. Check Holidays & Leaves
                cell.setHoliday(holidayDates.contains(cursor));

                LeaveRequest activeLeave = getActiveLeaveForDate(empLeaves, cursor);
                if (activeLeave != null) {
                    cell.setOnLeave(true);
                    cell.setLeaveSession(getSessionForDate(activeLeave, cursor));
                }

                // B. Resolve Shifts & Overlaps
                final LocalDate currentDay = cursor;
                List<ShiftAssignment> assignmentsToday = empAssignments.stream()
                        .filter(a -> !currentDay.isBefore(a.getStartDate()) && !currentDay.isAfter(a.getEndDate()))
                        .toList();

                ShiftAssignment bestAssignment = null;
                if (!assignmentsToday.isEmpty()) {
                    // Priority Logic: A 1-day assignment (Overtime) overrides a long-term monthly assignment
                    bestAssignment = assignmentsToday.stream()
                            .filter(a -> a.getStartDate().equals(a.getEndDate())) // Single day override
                            .findFirst()
                            .orElse(assignmentsToday.get(0));
                }

                if (bestAssignment != null) {
                    cell.setAssignment(mapToDTO(bestAssignment));
                    cell.setOvertimeOverride(bestAssignment.getStartDate().equals(bestAssignment.getEndDate()));

                    // If it's NOT a manual 1-day override, check if it's an off-day based on the Shift's working days
                    if (!cell.isOvertimeOverride()) {
                        String currentDayName = cursor.getDayOfWeek().name();
                        boolean isWorkingDay = bestAssignment.getShift().getWorkingDays().stream()
                                .anyMatch(w -> w.name().equals(currentDayName));

                        if (!isWorkingDay) {
                            cell.setOffDay(true);
                        }
                    }
                }

                // C. Overtime OVERRIDES Holidays and Off-days
                if (cell.isOvertimeOverride()) {
                    cell.setHoliday(false);
                    cell.setOffDay(false);
                }

                resolvedData.add(cell);
                cursor = cursor.plusDays(1);
            }
        }
        return resolvedData;
    }

    @Transactional
    public void deleteAssignmentRange(Long employeeId, LocalDate requestedStart, LocalDate requestedEnd) {
        LocalDate today = LocalDate.now();
        LocalDate earliestDeletableDate = today;

        // 1. Determine if "Today" is locked due to existing punches
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        List<TimeLog> todayPunches = timeLogRepository.findPunchesInShiftWindow(employeeId, startOfDay, endOfDay);
        if (!todayPunches.isEmpty()) {
            // Employee has already started working today; lock today.
            earliestDeletableDate = today.plusDays(1);
        }

        // 2. Validate and apply Smart Trim
        if (requestedEnd.isBefore(earliestDeletableDate)) {
            // The entire requested range is in the past/locked. Block it completely.
            throw new IllegalArgumentException("Cannot delete past shifts or active shifts where the employee has already punched in.");
        }

        // Trim the start date: if they asked to delete from Monday, but Monday/Tuesday are locked,
        // the actual deletion starts on Wednesday.
        LocalDate actualDeleteStart = requestedStart.isBefore(earliestDeletableDate)
                ? earliestDeletableDate
                : requestedStart;

        // 3. Slice out the legally deletable date range
        punchHoleInExistingShifts(employeeId, actualDeleteStart, requestedEnd);

        // 4. Audit the deletion (logging both what they requested and what actually happened)
        auditLoggingService.saveAuditLog(null, "RANGE_DELETED", "shift_assignments", null,
                String.format("{ \"employeeId\": %d, \"actualStart\": \"%s\", \"actualEnd\": \"%s\", \"requestedStart\": \"%s\" }",
                        employeeId, actualDeleteStart, requestedEnd, requestedStart));

        // 5. Recalculate attendance for the empty hole
        // (This sweeps the newly deleted future dates to ensure any pending flags are cleared)
        triggerAttendanceRecalculationIfPast(employeeId, actualDeleteStart, requestedEnd);
    }
    @Transactional
    public void editOrMoveShiftSegment(Long employeeId, LocalDate originalDate, LocalDate newDate, Long shiftId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ShiftNotFoundException("Shift not found"));

        LocalDate today = LocalDate.now();
        if (!originalDate.isAfter(today) || !newDate.isAfter(today)) {
            throw new IllegalArgumentException("Cannot modify or move shifts in the past or present.");
        }

        // 1. If the date was changed, "Punch Out" (delete) the shift from the original day
        if (!originalDate.equals(newDate)) {
            // We reuse your existing Smart Trim logic to slice the old day out
            deleteAssignmentRange(employeeId, originalDate, originalDate);
        }

        // 2. Clear any existing shifts on the target date (Overwrite behavior)
        punchHoleInExistingShifts(employeeId, newDate, newDate);

        // 3. Create the new 1-day shift assignment on the target date
        ShiftAssignment newAssignment = new ShiftAssignment();
        newAssignment.setEmployee(employee);
        newAssignment.setShift(shift);
        newAssignment.setStartDate(newDate);
        newAssignment.setEndDate(newDate);

        ShiftAssignment saved = shiftAssignmentRepository.saveAndFlush(newAssignment);

        auditLoggingService.saveAuditLog(saved.getId(), "SHIFT_MOVED_EDITED", "shift_assignments", null,
                String.format("{ \"employeeId\": %d, \"oldDate\": \"%s\", \"newDate\": \"%s\", \"shiftId\": %d }",
                        employeeId, originalDate, newDate, shift.getId()));

        // 4. Recalculate attendance for both the old empty hole and the new assigned day
        if (!originalDate.equals(newDate)) {
            triggerAttendanceRecalculationIfPast(employeeId, originalDate, originalDate);
        }
        triggerAttendanceRecalculationIfPast(employeeId, newDate, newDate);
    }
}