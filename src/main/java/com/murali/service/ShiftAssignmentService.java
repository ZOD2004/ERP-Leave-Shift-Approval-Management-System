package com.murali.service;

import com.murali.dto.ShiftAssignmentDTO;
import com.murali.entity.Employee;
import com.murali.entity.Shift;
import com.murali.entity.ShiftAssignment;
import com.murali.exception.EmployeeNotFoundException;
import com.murali.exception.ShiftConflictException;
import com.murali.exception.ShiftNotFoundException;
import com.murali.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        ShiftAssignment savedAssignment=shiftAssignmentRepository.save(assignment);

        return new ShiftAssignmentDTO(
                savedAssignment.getId(),
                employee.getId(),
                employee.getFirstName(),
                shift.getId(),
                shift.getName(),
                shift.getShiftType(),
                savedAssignment.getAssignmentDate()
        );
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

            result.add(
                    new ShiftAssignmentDTO(
                            saved.getId(),
                            employee.getId(),
                            employee.getFirstName(),
                            shift.getId(),
                            shift.getName(),
                            shift.getShiftType(),
                            saved.getAssignmentDate()
                    )
            );
        }
        return result;

    }

    private List<ShiftAssignmentDTO> mapToDTOList(List<ShiftAssignment> shiftAssignments) {

        return shiftAssignments.stream()
                .map(shiftAssignment -> new ShiftAssignmentDTO(
                        shiftAssignment.getId(),
                        shiftAssignment.getEmployee().getId(),
                        shiftAssignment.getEmployee().getFirstName(),
                        shiftAssignment.getShift().getId(),
                        shiftAssignment.getShift().getName(),
                        shiftAssignment.getShift().getShiftType(),
                        shiftAssignment.getAssignmentDate()
                ))
                .toList();
    }

    public Page<ShiftAssignmentDTO> fetchAssignmentsForGrid(int offset,int limit,
            LocalDate filterDate,String employeeNameSearch){
        int pageNumber = offset / limit;
        Pageable pageable = PageRequest.of(pageNumber, limit);

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

        return assignmentPage.map(assignment -> new ShiftAssignmentDTO(
                assignment.getId(),
                assignment.getEmployee().getId(),
                assignment.getEmployee().getFirstName(),
                assignment.getShift().getId(),
                assignment.getShift().getName(),
                assignment.getShift().getShiftType(),
                assignment.getAssignmentDate()
        ));
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

}