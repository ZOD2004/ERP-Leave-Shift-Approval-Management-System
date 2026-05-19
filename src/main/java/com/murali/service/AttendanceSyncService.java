package com.murali.service;

import com.murali.entity.Attendance;
import com.murali.entity.Employee;
import com.murali.entity.Holiday;
import com.murali.entity.LeaveRequest;
import com.murali.repository.AttendanceRepository;
import com.murali.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AttendanceSyncService {

    private final AttendanceRepository attendanceRepository;
    private final HolidayRepository holidayRepository;

    // Status Constants
    public static final String STATUS_ON_LEAVE = "ON_LEAVE";
    public static final String STATUS_PRESENT = "PRESENT";

    public AttendanceSyncService(AttendanceRepository attendanceRepository, HolidayRepository holidayRepository) {
        this.attendanceRepository = attendanceRepository;
        this.holidayRepository = holidayRepository;
    }

    /**
     * 1. Record Creation: Syncs approved leave to the attendance calendar.
     * Called by ApprovalRoutingService upon final approval.
     */
    @Transactional
    public void syncLeaveRecords(LeaveRequest request) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        Long employeeId = request.getEmployee().getId();

        List<Attendance> existingRecords = attendanceRepository
                .findByEmployeeIdAndAttendanceDateBetween(employeeId, startDate, endDate);

        Map<LocalDate, Attendance> attendanceMap = existingRecords.stream()
                .collect(Collectors.toMap(Attendance::getAttendanceDate, a -> a));

        List<Attendance> recordsToSave = new ArrayList<>();
        LocalDate currentDate = startDate;
        List<LocalDate> holidays = holidayRepository.findHolidayDatesBetween(startDate,endDate);

        while (!currentDate.isAfter(endDate)) {

            if(holidays.contains(currentDate) || currentDate.getDayOfWeek() == DayOfWeek.SATURDAY || currentDate.getDayOfWeek() == DayOfWeek.SUNDAY){
                currentDate = currentDate.plusDays(1);
                continue;
            }

            Attendance attendance = attendanceMap.getOrDefault(currentDate, new Attendance());

            if (attendance.getId() == null) {
                attendance.setEmployee(request.getEmployee());
                attendance.setAttendanceDate(currentDate);
            }

            if (request.getDurationDays().compareTo(new BigDecimal("0.5")) == 0) {
                attendance.setStatus("HALF_DAY_LEAVE");
            } else {
                attendance.setStatus("ON_LEAVE");
            }

            recordsToSave.add(attendance);
            currentDate = currentDate.plusDays(1);
        }

        if (!recordsToSave.isEmpty()) {
            attendanceRepository.saveAll(recordsToSave);
        }
    }

    /**
     * 2. Conflict Checking: Prevents checking in if the employee is on leave.
     * This would be called by your Biometric/Punch-in API endpoint.
     */

    // TODO: implement view for it
    @Transactional
    public Attendance processCheckIn(Employee employee, LocalDateTime punchTime) {
        LocalDate today = punchTime.toLocalDate();

        Attendance attendance = attendanceRepository
                .findByEmployeeIdAndAttendanceDate(employee.getId(), today)
                .orElseGet(() -> {
                    Attendance newAttendance = new Attendance();
                    newAttendance.setEmployee(employee);
                    newAttendance.setAttendanceDate(today);
                    return newAttendance;
                });

        if (STATUS_ON_LEAVE.equalsIgnoreCase(attendance.getStatus())) {
            throw new IllegalStateException(
                    "Check-in denied: You are marked as ON LEAVE for today. " +
                            "If this is a mistake, please cancel your leave request first."
            );
        }

        attendance.setCheckIn(punchTime);
        attendance.setStatus(STATUS_PRESENT);

        return attendanceRepository.save(attendance);
    }

    /**
     * 3. Auto Attendance Reversals: Reverts the attendance calendar when an approved leave is cancelled.
     * Called by LeaveRequestService during the cancellation workflow.
     */
    @Transactional
    public void revertLeaveFromAttendance(LeaveRequest request) {
        Long employeeId = request.getEmployee().getId();
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();

        // Fetch all attendance records for this employee within the cancelled date range
        List<Attendance> existingRecords = attendanceRepository
                .findByEmployeeIdAndAttendanceDateBetween(employeeId, startDate, endDate);

        List<Attendance> recordsToDelete = new ArrayList<>();
        List<Attendance> recordsToUpdate = new ArrayList<>();

        for (Attendance attendance : existingRecords) {
            String currentStatus = attendance.getStatus();

            // Only modify records that were explicitly marked as leave
            if (STATUS_ON_LEAVE.equals(currentStatus) || "HALF_DAY_LEAVE".equals(currentStatus)) {

                // If there is no check-in/check-out data, this record was purely a placeholder. Safe to delete.
                if (attendance.getCheckIn() == null && attendance.getCheckOut() == null) {
                    recordsToDelete.add(attendance);
                } else {
                    // If there IS a check-in time, revert the status back to PRESENT
                    // so the employee's actual worked hours are preserved.
                    attendance.setStatus(STATUS_PRESENT);
                    recordsToUpdate.add(attendance);
                }
            }
        }

        // Execute batch database operations
        if (!recordsToDelete.isEmpty()) {
            attendanceRepository.deleteAll(recordsToDelete);
        }

        if (!recordsToUpdate.isEmpty()) {
            attendanceRepository.saveAll(recordsToUpdate);
        }
    }
}
