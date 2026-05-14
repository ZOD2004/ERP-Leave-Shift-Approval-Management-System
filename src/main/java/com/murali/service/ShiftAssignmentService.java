package com.murali.service;

import com.murali.dto.BatchPreviewResponse;
import com.murali.dto.ShiftAssignmentDTO;
import com.murali.dto.ShiftConflictDTO;
import com.murali.entity.Employee;
import com.murali.entity.LeaveRequest;
import com.murali.entity.Shift;
import com.murali.entity.ShiftAssignment;
import com.murali.exception.EmployeeNotFoundException;
import com.murali.exception.ShiftConflictException;
import com.murali.exception.ShiftNotFoundException;
import com.murali.repository.*;
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

@Service
public class ShiftAssignmentService {

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftRepository shiftRepository;

    public ShiftAssignmentService(ShiftAssignmentRepository shiftAssignmentRepository, HolidayRepository holidayRepository, LeaveRequestRepository leaveRequestRepository, EmployeeRepository employeeRepository, ShiftRepository shiftRepository) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.holidayRepository = holidayRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
    }


    public void validateShiftConflict(Long employeeId, LocalDate assignmentDate) {

        boolean hasExistingShift =
                shiftAssignmentRepository.existsByEmployeeIdAndAssignmentDate(employeeId,assignmentDate);
        if (hasExistingShift) {
            throw new ShiftConflictException("Employee already has a shift scheduled on " + assignmentDate);
        }

        boolean isOnLeave =
                leaveRequestRepository.isEmployeeOnApprovedLeave(employeeId,"FULLY_APPROVED",assignmentDate);

        if (isOnLeave) {
            throw new ShiftConflictException("Employee is on approved leave on " + assignmentDate);
        }
    }

    public ShiftAssignmentDTO assignSingleShift(ShiftAssignmentDTO assignmentDTO) {

        validateShiftConflict(assignmentDTO.getEmployeeId(),assignmentDTO.getAssignmentDate());

        Employee employee = employeeRepository.findById(assignmentDTO.getEmployeeId()).orElseThrow(() ->
                new EmployeeNotFoundException("Employee not found with ID: "+ assignmentDTO.getEmployeeId()));

        Shift shift = shiftRepository.findById(assignmentDTO.getShiftId())
                .orElseThrow(()->new ShiftNotFoundException("Shift not found with ID: "+ assignmentDTO.getShiftId()));

        ShiftAssignment assignment = new ShiftAssignment();

        assignment.setEmployee(employee);
        assignment.setShift(shift);
        assignment.setAssignmentDate(assignmentDTO.getAssignmentDate());

        ShiftAssignment savedAssignment = shiftAssignmentRepository.save(assignment);
        return mapToDTO(savedAssignment);
    }


    @Transactional
    public List<ShiftAssignmentDTO> assignShiftsBulk(Long employeeId, Long shiftId,
                                                     LocalDate startDate, LocalDate endDate){

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: "+employeeId));


        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ShiftNotFoundException("Shift not found with id: "+shiftId));

        List<LocalDate> holidayDates = holidayRepository.findHolidayDatesBetween(startDate, endDate);

        List<LocalDate> approvedLeaveDates = leaveRequestRepository.
                findApprovedLeaveDatesForEmployee(employeeId, "FULLY_APPROVED", startDate, endDate);

        List<ShiftAssignment> assignmentsToSave = new ArrayList<>();
        List<ShiftAssignmentDTO> result = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {

            boolean isWeekend = (currentDate.getDayOfWeek().getValue() >= 6); // 6 = Saturday, 7 = Sunday
            boolean isHoliday = holidayDates.contains(currentDate);
            boolean isOnLeave = approvedLeaveDates.contains(currentDate);

            // Check if they already have a shift scheduled that day
            boolean hasExistingShift = shiftAssignmentRepository.existsByEmployeeIdAndAssignmentDate(employeeId, currentDate);
            validateShiftConflict(employeeId,currentDate);

            if (!isWeekend && !isHoliday && !isOnLeave && !hasExistingShift) {

                ShiftAssignment assignment = new ShiftAssignment();
                assignment.setEmployee(employee);
                assignment.setShift(shift);
                assignment.setAssignmentDate(currentDate);

                assignmentsToSave.add(assignment);
            }

            currentDate = currentDate.plusDays(1);
        }
        List<ShiftAssignment> savedAssignments = shiftAssignmentRepository.saveAll(assignmentsToSave);

        for (ShiftAssignment saved : savedAssignments) {
            result.add(mapToDTO(saved));
        }
        return result;

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
    public BatchPreviewResponse previewBatchAssignments(List<Long> employeeIds, Long shiftId,
                                                        LocalDate startDate, String duration) {
        BatchPreviewResponse response = new BatchPreviewResponse();

        // 1. Calculate end date
        LocalDate endDate = calculateEndDate(startDate, duration);

        // 2. Fetch base entities directly (Throws exception immediately if missing to avoid Optional wrappers)
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ShiftNotFoundException("Shift not found with ID: " + shiftId));

        Map<Long, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, emp -> emp));

        // 3. Pre-fetch blockouts and existing assignments (Bulk Fetching)
        List<LocalDate> holidayDates = holidayRepository.findHolidayDatesBetween(startDate, endDate);

        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(
                employeeIds, "FULLY_APPROVED", startDate, endDate);

        // Group leaves by Employee ID for fast lookup
        Map<Long, List<LeaveRequest>> leavesByEmployee = leaves.stream()
                .collect(Collectors.groupingBy(l -> l.getEmployee().getId()));

        List<ShiftAssignment> existingAssignments = shiftAssignmentRepository
                .findByEmployeeIdInAndAssignmentDateBetween(employeeIds, startDate, endDate);

        // Map assignments using a composite key: EmployeeId_Date
        Map<String, ShiftAssignment> assignmentsMap = existingAssignments.stream()
                .collect(Collectors.toMap(a -> a.getEmployee().getId() + "_" + a.getAssignmentDate(), a -> a));

        // 4. The Validation Loop
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {

            boolean isWeekend = (currentDate.getDayOfWeek().getValue() >= 6); // 6=Saturday, 7=Sunday
            boolean isHoliday = holidayDates.contains(currentDate);

            for (Long empId : employeeIds) {
                Employee employee = employeeMap.get(empId);
                if (employee == null) continue;

                String mapKey = empId + "_" + currentDate;
                boolean hasExistingShift = assignmentsMap.containsKey(mapKey);

                List<LeaveRequest> empLeaves = leavesByEmployee.getOrDefault(empId, Collections.emptyList());
                LeaveRequest activeLeave = getActiveLeaveForDate(empLeaves, currentDate);

                if (hasExistingShift || isHoliday || (activeLeave != null && isFullDayLeave(activeLeave))) {

                    // --- HARD CONFLICT ---
                    ShiftConflictDTO conflict = createConflictBase(employee, shift, currentDate);

                    if (hasExistingShift) conflict.setConflictType("Overlap");
                    else if (isHoliday) conflict.setConflictType("Holiday");
                    else conflict.setConflictType("Full Leave");

                    response.getHardConflicts().add(conflict);

                } else if (activeLeave != null && isHalfDayLeave(activeLeave)) {

                    // --- PARTIAL CONFLICT (Half-Day) ---
                    ShiftConflictDTO conflict = createConflictBase(employee, shift, currentDate);
                    conflict.setConflictType("Half-Day Leave");
                    conflict.setStandardStartTime(shift.getStartTime());
                    conflict.setStandardEndTime(shift.getEndTime());

                    // Calculate midpoint for system resolution suggestion
                    long totalMinutes = java.time.Duration.between(shift.getStartTime(), shift.getEndTime()).toMinutes();
                    LocalTime secondHalfStart = shift.getStartTime().plusMinutes(totalMinutes / 2);

                    conflict.setSystemResolution("Leave: 1st Half | Work: " + secondHalfStart + " to " + shift.getEndTime());
                    conflict.setSuggestedOverrideStart(secondHalfStart);
                    conflict.setSuggestedOverrideEnd(shift.getEndTime());

                    response.getPartialConflicts().add(conflict);

                } else if (!isWeekend) {

                    // --- READY TO SAVE ---
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
        return response;
    }

    /**
     * 2. The Final Execution Engine
     */
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
        return leave.getDurationDays().compareTo(new BigDecimal("0.5")) > 0;
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
}