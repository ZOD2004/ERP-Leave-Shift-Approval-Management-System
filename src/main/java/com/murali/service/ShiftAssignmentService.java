package com.murali.service;

import com.murali.dto.*;
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
    private final AuditLogService auditLoggingService;
    private final TimeLogRepository timeLogRepository;
    private final ScheduleCalculationService scheduleCalculationService;
    private final AttendanceProcessService attendanceProcessService;

    public ShiftAssignmentService(ShiftAssignmentRepository shiftAssignmentRepository, HolidayRepository holidayRepository, LeaveRequestRepository leaveRequestRepository, EmployeeRepository employeeRepository, ShiftRepository shiftRepository, AuditLogService auditLoggingService, TimeLogRepository timeLogRepository,ScheduleCalculationService scheduleCalculationService, AttendanceProcessService attendanceProcessService) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.holidayRepository = holidayRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
        this.auditLoggingService = auditLoggingService;
        this.timeLogRepository = timeLogRepository;
        this.scheduleCalculationService = scheduleCalculationService;
        this.attendanceProcessService = attendanceProcessService;
    }

    public BatchPreviewResponse previewBatchAssignments(List<Long> employeeIds, Long shiftId, LocalDate startDate, LocalDate endDate) {
        BatchPreviewResponse response = new BatchPreviewResponse();

        Shift shift = shiftRepository.findById(shiftId).orElseThrow(() -> new IllegalArgumentException("Shift not found with ID: " + shiftId));

        Map<Long, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream().collect(Collectors.toMap(Employee::getId, emp -> emp));

        Set<LocalDate> holidayDates = new HashSet<>(holidayRepository.findHolidayDatesBetween(startDate, endDate));

        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(employeeIds, "APPROVED", startDate, endDate);

        Map<Long, List<LeaveRequest>> leavesByEmployee = leaves.stream().collect(Collectors.groupingBy(l -> l.getEmployee().getId()));

        List<ShiftAssignment> existingAssignments = shiftAssignmentRepository.findByEmployeeIdInAndDateRange(employeeIds, startDate, endDate);

        LocalDate currentDate = startDate;
        Map<Long, List<LocalDate>> validDaysPerEmployee = new HashMap<>();

        while (!currentDate.isAfter(endDate)) {
            boolean isHoliday = holidayDates.contains(currentDate);
            String currentDayName = currentDate.getDayOfWeek().name();

            boolean isShiftWorkingDay = shift.getWorkingDays().stream().anyMatch(wd -> wd.name().equalsIgnoreCase(currentDayName));

            for (Long empId : employeeIds) {
                Employee employee = employeeMap.get(empId);
                if (employee == null) continue;

                List<LeaveRequest> empLeaves = leavesByEmployee.getOrDefault(empId, Collections.emptyList());
                LeaveRequest activeLeave = getActiveLeaveForDate(empLeaves, currentDate);
                boolean hasExistingShift = isEmployeeAssignedOnDate(existingAssignments, empId, currentDate);
                boolean isInvalidDay = !isShiftWorkingDay;

                boolean isSingleDayOverride = startDate.equals(endDate);

                boolean blocksAssignment = activeLeave != null || hasExistingShift ||
                        (!isSingleDayOverride && (isHoliday || isInvalidDay));

                if (blocksAssignment) {

                    ShiftConflictDTO conflict = createConflictBase(employee, shift, currentDate);

                    if (activeLeave != null) {
                        LeaveSession session = getSessionForDate(activeLeave, currentDate);
                        if (session == LeaveSession.FULL_DAY) {
                            conflict.setConflictType("Full-Day Leave");
                        } else {
                            conflict.setConflictType("Half-Day Leave (" + session.name() + ")");
                        }
                    } else if (hasExistingShift) {
                        conflict.setConflictType("Overlap");
                    } else if (isHoliday) {
                        conflict.setConflictType("Holiday");
                    } else {
                        conflict.setConflictType("Non-Working Day");
                    }

                    response.getHardConflicts().add(conflict);
                } else {
                    validDaysPerEmployee.computeIfAbsent(empId, k -> new ArrayList<>()).add(currentDate);
                }
            }
            currentDate = currentDate.plusDays(1);
        }

        response.setReadyToSave(groupValidDaysIntoRanges(validDaysPerEmployee, employeeMap, shift));
        return response;
    }

    @Transactional
    public void saveResolvedBatch(List<ShiftAssignmentDTO> finalCleanAssignments, boolean allowOverwrite) {
        if (finalCleanAssignments == null || finalCleanAssignments.isEmpty()) return;

        Set<Long> employeeIds = finalCleanAssignments.stream().map(ShiftAssignmentDTO::getEmployeeId).collect(Collectors.toSet());
        Set<Long> shiftIds = finalCleanAssignments.stream().map(ShiftAssignmentDTO::getShiftId).collect(Collectors.toSet());

        Map<Long, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream().collect(Collectors.toMap(Employee::getId, emp -> emp));
        Map<Long, Shift> shiftMap = shiftRepository.findAllById(shiftIds).stream().collect(Collectors.toMap(Shift::getId, shift -> shift));

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


    private void punchHoleInExistingShifts(Long employeeId, LocalDate newStart, LocalDate newEnd) {
        List<ShiftAssignment> overlaps = shiftAssignmentRepository.findByEmployeeIdInAndDateRange(List.of(employeeId), newStart, newEnd);

        for (ShiftAssignment old : overlaps) {
            LocalDate oldStart = old.getStartDate();
            LocalDate oldEnd = old.getEndDate();

            if (!newStart.isAfter(oldStart) && !newEnd.isBefore(oldEnd)) {//newStart <= oldStart AND newEnd >= oldEnd
                shiftAssignmentRepository.delete(old);
                shiftAssignmentRepository.flush();
            } else if (!newStart.isAfter(oldStart) && newEnd.isBefore(oldEnd)) { //newStart <= oldStart AND newEnd < oldEnd
                old.setStartDate(newEnd.plusDays(1));
                shiftAssignmentRepository.saveAndFlush(old);
            } else if (newStart.isAfter(oldStart) && !newEnd.isBefore(oldEnd)) {//newStart > oldStart AND newEnd >= oldEnd
                old.setEndDate(newStart.minusDays(1));
                shiftAssignmentRepository.saveAndFlush(old);
            } else if (newStart.isAfter(oldStart) && newEnd.isBefore(oldEnd)) {//newStart > oldStart AND newEnd < oldEnd
                ShiftAssignment remainder = new ShiftAssignment();
                remainder.setEmployee(old.getEmployee());
                remainder.setShift(old.getShift());
                remainder.setStartDate(newEnd.plusDays(1));
                remainder.setEndDate(old.getEndDate());

                old.setEndDate(newStart.minusDays(1));
                shiftAssignmentRepository.save(old);
                shiftAssignmentRepository.saveAndFlush(remainder);
            }

            triggerAttendanceRecalculationIfPast(employeeId, oldStart, oldEnd);
        }
    }

    @Transactional
    public void updateSingleAssignment(ShiftAssignmentDTO dto) {
        ShiftAssignment existing = shiftAssignmentRepository.findById(dto.getId())
                .orElseThrow(() -> new EntityNotFoundException("Shift Assignment not found with ID: " + dto.getId()));

        Shift newShift = shiftRepository.findById(dto.getShiftId())
                .orElseThrow(() -> new ShiftNotFoundException("Shift not found with ID: " + dto.getShiftId()));

        LocalDate today = LocalDate.now();
        LocalDate originalStart = existing.getStartDate();
        LocalDate originalEnd = existing.getEndDate();

        LocalDate newStart = dto.getStartDate();
        LocalDate newEnd = dto.getEndDate();

        if (originalStart.isAfter(today)) {
            boolean hasConflict = shiftAssignmentRepository.existsConflictExcludingAssignment(dto.getEmployeeId(), newStart, newEnd, existing.getId());
            if (hasConflict) throw new ShiftConflictException("Cannot update boundaries: Overlaps with another assigned shift.");

            String oldState = String.format("{ \"shiftId\": %d, \"startDate\": \"%s\", \"endDate\": \"%s\" }", existing.getShift().getId(), originalStart, originalEnd);

            existing.setShift(newShift);
            existing.setStartDate(newStart);
            existing.setEndDate(newEnd);
            shiftAssignmentRepository.saveAndFlush(existing);

            auditLoggingService.saveAuditLog(existing.getId(), "UPDATED", "shift_assignments", oldState,
                    String.format("{ \"shiftId\": %d, \"startDate\": \"%s\", \"endDate\": \"%s\" }", newShift.getId(), newStart, newEnd));
            return;
        }
        if (!newStart.isAfter(today)) {
            throw new IllegalArgumentException("Cannot apply edits to past or active dates. Effective start date must be tomorrow or later.");
        }

        existing.setEndDate(newStart.minusDays(1));
        shiftAssignmentRepository.saveAndFlush(existing);

        punchHoleInExistingShifts(existing.getEmployee().getId(), newStart, newEnd);

        ShiftAssignment newSegment = new ShiftAssignment();
        newSegment.setEmployee(existing.getEmployee());
        newSegment.setShift(newShift);
        newSegment.setStartDate(newStart);
        newSegment.setEndDate(newEnd);
        ShiftAssignment savedSegment = shiftAssignmentRepository.saveAndFlush(newSegment);

        auditLoggingService.saveAuditLog(existing.getId(), "SPLIT_AND_UPDATED", "shift_assignments",
                String.format("{ \"originalEnd\": \"%s\" }", originalEnd),
                String.format("{ \"newSegmentId\": %d, \"newStart\": \"%s\", \"newEnd\": \"%s\", \"shiftId\": %d }", savedSegment.getId(), newStart, newEnd, newShift.getId()));
    }
    @Transactional
    public void deleteAssignment(Long id) {
        ShiftAssignment existing = shiftAssignmentRepository.findById(id).orElseThrow(() -> new ShiftNotFoundException("Assignment not found"));

        Long empId = existing.getEmployee().getId();
        LocalDate start = existing.getStartDate();
        LocalDate end = existing.getEndDate();

        shiftAssignmentRepository.deleteById(id);
        auditLoggingService.saveAuditLog(id, "DELETED", "shift_assignments", String.format("{ \"shiftId\": %d }", existing.getShift().getId()), null);

        triggerAttendanceRecalculationIfPast(empId, start, end);
    }

    public Page<ShiftAssignmentDTO> fetchAssignmentsForGrid(int offset, int limit, LocalDate filterDate, String employeeNameSearch) {
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
                    currentEnd = nextDay;
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
            attendanceProcessService.recalculateAttendanceForDate(employeeId, cursor);
            cursor = cursor.plusDays(1);
        }
    }

    private boolean isEmployeeAssignedOnDate(List<ShiftAssignment> existingAssignments, Long empId, LocalDate targetDate) {
        return existingAssignments.stream().anyMatch(a -> a.getEmployee().getId().equals(empId) && !targetDate.isBefore(a.getStartDate()) && !targetDate.isAfter(a.getEndDate()));
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
        return leaves.stream().filter(l -> !targetDate.isBefore(l.getStartDate()) && !targetDate.isAfter(l.getEndDate())).findFirst().orElse(null);
    }

    private LeaveSession getSessionForDate(LeaveRequest request, LocalDate targetDate) {
        if (request == null) return null;

        if (targetDate.equals(request.getStartDate()) && targetDate.equals(request.getEndDate())) {
            return request.getStartSession();
        }

        if (targetDate.equals(request.getStartDate())) {
            return request.getStartSession();
        }
        if (targetDate.equals(request.getEndDate())) {
            return request.getEndSession();
        }
        return LeaveSession.FULL_DAY;
    }


    @Transactional(readOnly = true)
    public Map<String, Long> getTodayShiftCounts(LocalDate date) {
        List<Employee> activeEmployees = employeeRepository.findByActiveTrue();

        Map<Long, List<DailyExpectedShift>> batchSchedules = scheduleCalculationService.calculateBatchShifts(activeEmployees, date, date);

        Map<String, Long> stats = new java.util.HashMap<>();

        for (Employee emp : activeEmployees) {
            List<DailyExpectedShift> expectations = batchSchedules.getOrDefault(emp.getId(), Collections.emptyList());
            if (!expectations.isEmpty()) {
                DailyExpectedShift expected = expectations.get(0);

                if (expected.isWorkingDay() && expected.getExpectedShift() != null) {
                    String shiftName = expected.getExpectedShift().getName();
                    stats.put(shiftName, stats.getOrDefault(shiftName, 0L) + 1L);
                }
            }
        }
        return stats;
    }

    public List<DailyCellDTO> getResolvedCalendarData(LocalDate startDate, LocalDate endDate) {
        List<Employee> activeEmployees = employeeRepository.findByActiveTrue();

        Map<Long, List<DailyExpectedShift>> batchSchedules = scheduleCalculationService.calculateBatchShifts(activeEmployees, startDate, endDate);

        List<DailyCellDTO> resolvedData = new ArrayList<>();

        for (Employee emp : activeEmployees) {
            List<DailyExpectedShift> expectations = batchSchedules.getOrDefault(emp.getId(), Collections.emptyList());

            for (DailyExpectedShift expected : expectations) {
                DailyCellDTO cell = new DailyCellDTO();
                cell.setDate(expected.getTargetDate());
                cell.setEmployeeId(emp.getId());
                cell.setEmployeeName(emp.getFirstName());

                cell.setHoliday(expected.isHoliday());
                cell.setIntentionalOffDay(expected.isIntentionalOffDay());

                if (expected.getActiveLeave() != null) {
                    cell.setOnLeave(true);
                    cell.setLeaveSession(expected.getLeaveSession());
                }

                if (expected.isWorkingDay() && expected.getExpectedShift() != null) {
                    Shift virtualShift = expected.getExpectedShift();

                    ShiftAssignmentDTO shiftDto = new ShiftAssignmentDTO();
                    shiftDto.setId(expected.getAssignmentId());
                    shiftDto.setShiftId(virtualShift.getId());
                    shiftDto.setShiftName(virtualShift.getName());
                    shiftDto.setShiftType(virtualShift.getShiftType());
                    shiftDto.setIsRotational(Boolean.TRUE.equals(virtualShift.getIsRotationalShift()));
                    shiftDto.setSegmentName(virtualShift.getActiveSegmentName());
                    shiftDto.setStartTime(virtualShift.getStartTime());
                    shiftDto.setEndTime(virtualShift.getEndTime());
                    shiftDto.setStartDate(expected.getTargetDate());
                    shiftDto.setEndDate(expected.getTargetDate());

                    cell.setAssignment(shiftDto);
                    cell.setOvertimeOverride(expected.isManualOverride());
                }

                resolvedData.add(cell);
            }
        }
        return resolvedData;
    }

    @Transactional
    public void deleteAssignmentRange(Long employeeId, LocalDate requestedStart, LocalDate requestedEnd) {
        LocalDate today = LocalDate.now();
        LocalDate earlyDate = today;

        List<TimeLog> todayPunches = timeLogRepository.findByEmployeeIdAndAttendanceDate(employeeId, today);

        if (!todayPunches.isEmpty()) {
            earlyDate = today.plusDays(1);
        }

        if (requestedEnd.isBefore(earlyDate)) {
            throw new IllegalArgumentException("Cannot delete past shifts or active shifts where the employee has already punched in.");
        }

        LocalDate actualDeleteStart = requestedStart.isBefore(earlyDate) ? earlyDate : requestedStart;

        punchHoleInExistingShifts(employeeId, actualDeleteStart, requestedEnd);

        auditLoggingService.saveAuditLog(null, "RANGE_DELETED", "shift_assignments", null, String.format("{ \"employeeId\": %d, \"actualStart\": \"%s\", \"actualEnd\": \"%s\", \"requestedStart\": \"%s\" }", employeeId, actualDeleteStart, requestedEnd, requestedStart));

        triggerAttendanceRecalculationIfPast(employeeId, actualDeleteStart, requestedEnd);
    }

    @Transactional
    public void editOrMoveShiftSegment(Long employeeId, LocalDate originalDate, LocalDate newDate, Long shiftId) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        Shift shift = shiftRepository.findById(shiftId).orElseThrow(() -> new ShiftNotFoundException("Shift not found"));

        LocalDate today = LocalDate.now();
        if (!originalDate.isAfter(today) || !newDate.isAfter(today)) {
            throw new IllegalArgumentException("Cannot modify or move shifts in the past or present.");
        }

        if (!originalDate.equals(newDate)) {
            deleteAssignmentRange(employeeId, originalDate, originalDate);
        }

        punchHoleInExistingShifts(employeeId, newDate, newDate);

        ShiftAssignment newAssignment = new ShiftAssignment();
        newAssignment.setEmployee(employee);
        newAssignment.setShift(shift);
        newAssignment.setStartDate(newDate);
        newAssignment.setEndDate(newDate);

        ShiftAssignment saved = shiftAssignmentRepository.saveAndFlush(newAssignment);

        auditLoggingService.saveAuditLog(saved.getId(), "SHIFT_MOVED_EDITED", "shift_assignments", null, String.format("{ \"employeeId\": %d, \"oldDate\": \"%s\", \"newDate\": \"%s\", \"shiftId\": %d }", employeeId, originalDate, newDate, shift.getId()));

        if (!originalDate.equals(newDate)) {
            triggerAttendanceRecalculationIfPast(employeeId, originalDate, originalDate);
        }
        triggerAttendanceRecalculationIfPast(employeeId, newDate, newDate);
    }
}