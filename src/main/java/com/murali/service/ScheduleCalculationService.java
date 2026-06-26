package com.murali.service;

import com.murali.dto.DailyExpectedShift;
import com.murali.entity.*;
import com.murali.entity.enums.LeaveSession;
import com.murali.entity.enums.RotationSegmentType;
import com.murali.repository.HolidayRepository;
import com.murali.repository.LeaveRequestRepository;
import com.murali.repository.ShiftAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleCalculationService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final HolidayRepository holidayRepository;

    @Transactional(readOnly = true)
        public DailyExpectedShift calculateDailyShift(Employee employee, LocalDate targetDate) {
        DailyExpectedShift result = new DailyExpectedShift();
        result.setTargetDate(targetDate);
        result.setEmployeeId(employee.getId());
        Optional<LeaveRequest> activeLeaveOpt = leaveRequestRepository.findApprovedLeaveForEmployeeOnDate(employee.getId(), targetDate);
        if (activeLeaveOpt.isPresent()) {
            LeaveRequest leave = activeLeaveOpt.get();
            result.setActiveLeave(leave);
            result.setLeaveSession(getSessionForDate(leave, targetDate));

            if (result.getLeaveSession() == LeaveSession.FULL_DAY) {
                result.setWorkingDay(false);
                return result;
            }
        }
        Optional<ShiftAssignment> manualOverrideOpt = shiftAssignmentRepository.findAssignmentByEmployeeAndDate(employee.getId(), targetDate);
        if (manualOverrideOpt.isPresent()) {
            ShiftAssignment override = manualOverrideOpt.get();
            result.setExpectedShift(override.getShift());
            result.setAssignmentId(override.getId());
            result.setManualOverride(true);
            result.setWorkingDay(true);
            return result;
        }
        if (holidayRepository.existsByHolidayDate(targetDate)) {
            result.setHoliday(true);
            result.setWorkingDay(false);
            return result;
        }
        Shift defaultShift = employee.getDefaultShift();
        if (defaultShift == null) {
            result.setWorkingDay(false);
            result.setIntentionalOffDay(true);
            return result;
        }

        if (Boolean.TRUE.equals(defaultShift.getIsRotationalShift())) {
            LocalDate anchorDate = employee.getShiftEffectiveDate() != null ? employee.getShiftEffectiveDate() : LocalDate.now();
            List<RotationSequence> sequences = defaultShift.getRotationSequences();

            if (sequences == null || sequences.isEmpty()) {
                result.setWorkingDay(false);
                return result;
            }

            int cycleLength = sequences.stream().mapToInt(RotationSequence::getDurationDays).sum();
            if (cycleLength == 0) {
                result.setWorkingDay(false);
                return result;
            }

            long daysElapsed = ChronoUnit.DAYS.between(anchorDate, targetDate);
            int cyclePosition = (int) ((daysElapsed % cycleLength + cycleLength) % cycleLength);

            RotationSequence activeSegment = null;
            int currentDayCount = 0;
            for (RotationSequence seq : sequences) {
                currentDayCount += seq.getDurationDays();
                if (cyclePosition < currentDayCount) {
                    activeSegment = seq;
                    break;
                }
            }

            if (activeSegment == null || activeSegment.getSegmentType() == RotationSegmentType.OFF) {
                result.setWorkingDay(false);
                result.setIntentionalOffDay(true);
            } else {
                result.setWorkingDay(true);
                result.setExpectedShift(createVirtualShiftFromSegment(defaultShift, activeSegment));
            }

        } else {
            String dayName = targetDate.getDayOfWeek().name();
            boolean isWorkingDay = defaultShift.getWorkingDays().stream()
                    .anyMatch(wd -> wd.name().equalsIgnoreCase(dayName));

            if (isWorkingDay) {
                result.setWorkingDay(true);
                result.setExpectedShift(defaultShift);
            } else {
                result.setWorkingDay(false);
                result.setIntentionalOffDay(true);
            }
        }

        return result;
    }

    private Shift createVirtualShiftFromSegment(Shift parentShift, RotationSequence segment) {
        Shift virtualShift = new Shift();
        virtualShift.setId(parentShift.getId());
        virtualShift.setName(parentShift.getName() + " - " + segment.getName());
        virtualShift.setShiftType(segment.getShiftType());
        virtualShift.setStartTime(segment.getStartTime());
        virtualShift.setEndTime(segment.getEndTime());
        virtualShift.setRequiredWorkTime(segment.getRequiredWorkTime());
        virtualShift.setCrossesMidnight(segment.getCrossesMidnight());
        virtualShift.setFirstHalfEndTime(segment.getFirstHalfEndTime());
        virtualShift.setSecondHalfStartTime(segment.getSecondHalfStartTime());
        return virtualShift;
    }

    private LeaveSession getSessionForDate(LeaveRequest request, LocalDate targetDate) {
        if (request == null) return null;
        if (targetDate.equals(request.getStartDate()) && targetDate.equals(request.getEndDate())) return request.getStartSession();
        if (targetDate.equals(request.getStartDate())) return request.getStartSession();
        if (targetDate.equals(request.getEndDate())) return request.getEndSession();
        return LeaveSession.FULL_DAY;
    }
    @Transactional(readOnly = true)
    public Map<Long, List<DailyExpectedShift>> calculateBatchShifts(List<Employee> employees, LocalDate startDate, LocalDate endDate) {
        Map<Long, List<DailyExpectedShift>> bulkResults = new HashMap<>();
        if (employees == null || employees.isEmpty() || startDate.isAfter(endDate)) {
            return bulkResults;
        }

        List<Long> empIds = employees.stream().map(Employee::getId).toList();

        List<LeaveRequest> allLeaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(empIds, "APPROVED", startDate, endDate);
        List<ShiftAssignment> allOverrides = shiftAssignmentRepository.findByEmployeeIdInAndDateRange(empIds, startDate, endDate);
        Set<LocalDate> allHolidays = new HashSet<>(holidayRepository.findHolidayDatesBetween(startDate, endDate));

        Map<Long, List<LeaveRequest>> leavesByEmp = allLeaves.stream()
                .collect(Collectors.groupingBy(lr -> lr.getEmployee().getId()));
        Map<Long, List<ShiftAssignment>> overridesByEmp = allOverrides.stream()
                .collect(Collectors.groupingBy(sa -> sa.getEmployee().getId()));

        for (Employee employee : employees) {
            List<DailyExpectedShift> employeeSchedule = new ArrayList<>();
            Long empId = employee.getId();

            List<LeaveRequest> empLeaves = leavesByEmp.getOrDefault(empId, Collections.emptyList());
            List<ShiftAssignment> empOverrides = overridesByEmp.getOrDefault(empId, Collections.emptyList());

            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                DailyExpectedShift daily = new DailyExpectedShift();
                daily.setTargetDate(date);
                daily.setEmployeeId(empId);
                LeaveRequest activeLeave = getActiveLeaveForDate(empLeaves, date);
                if (activeLeave != null) {
                    daily.setActiveLeave(activeLeave);
                    daily.setLeaveSession(getSessionForDate(activeLeave, date));
                    if (daily.getLeaveSession() == LeaveSession.FULL_DAY) {
                        daily.setWorkingDay(false);
                        employeeSchedule.add(daily);
                        continue;
                    }
                }
                ShiftAssignment override = getActiveOverrideForDate(empOverrides, date);
                if (override != null) {
                    daily.setExpectedShift(override.getShift());
                    daily.setAssignmentId(override.getId());
                    daily.setManualOverride(true);
                    daily.setWorkingDay(true);
                    employeeSchedule.add(daily);
                    continue;
                }
                if (allHolidays.contains(date)) {
                    daily.setHoliday(true);
                    daily.setWorkingDay(false);
                    employeeSchedule.add(daily);
                    continue;
                }
                Shift defaultShift = employee.getDefaultShift();
                if (defaultShift == null) {
                    daily.setWorkingDay(false);
                    daily.setIntentionalOffDay(true);
                    employeeSchedule.add(daily);
                    continue;
                }

                if (Boolean.TRUE.equals(defaultShift.getIsRotationalShift())) {
                    LocalDate anchorDate = employee.getShiftEffectiveDate() != null ? employee.getShiftEffectiveDate() : LocalDate.now();
                    List<RotationSequence> sequences = defaultShift.getRotationSequences();

                    if (sequences == null || sequences.isEmpty()) {
                        daily.setWorkingDay(false);
                    } else {
                        int cycleLength = sequences.stream().mapToInt(RotationSequence::getDurationDays).sum();
                        if (cycleLength == 0) {
                            daily.setWorkingDay(false);
                        } else {
                            long daysElapsed = ChronoUnit.DAYS.between(anchorDate, date);
                            int cyclePosition = (int) ((daysElapsed % cycleLength + cycleLength) % cycleLength);

                            RotationSequence activeSegment = null;
                            int currentDayCount = 0;
                            for (RotationSequence seq : sequences) {
                                currentDayCount += seq.getDurationDays();
                                if (cyclePosition < currentDayCount) {
                                    activeSegment = seq;
                                    break;
                                }
                            }

                            if (activeSegment == null || activeSegment.getSegmentType() == RotationSegmentType.OFF) {
                                daily.setWorkingDay(false);
                                daily.setIntentionalOffDay(true);
                            } else {
                                daily.setWorkingDay(true);
                                daily.setExpectedShift(createVirtualShiftFromSegment(defaultShift, activeSegment));
                            }
                        }
                    }
                } else {
                    String dayName = date.getDayOfWeek().name();
                    boolean isWorkingDay = defaultShift.getWorkingDays().stream()
                            .anyMatch(wd -> wd.name().equalsIgnoreCase(dayName));

                    if (isWorkingDay) {
                        daily.setWorkingDay(true);
                        daily.setExpectedShift(defaultShift);
                    } else {
                        daily.setWorkingDay(false);
                        daily.setIntentionalOffDay(true);
                    }
                }
                employeeSchedule.add(daily);
            }
            bulkResults.put(empId, employeeSchedule);
        }
        return bulkResults;
    }

    private LeaveRequest getActiveLeaveForDate(List<LeaveRequest> leaves, LocalDate targetDate) {
        return leaves.stream()
                .filter(l -> !targetDate.isBefore(l.getStartDate()) && !targetDate.isAfter(l.getEndDate()))
                .findFirst()
                .orElse(null);
    }

    private ShiftAssignment getActiveOverrideForDate(List<ShiftAssignment> overrides, LocalDate targetDate) {
        return overrides.stream()
                .filter(a -> !targetDate.isBefore(a.getStartDate()) && !targetDate.isAfter(a.getEndDate()))
                .findFirst()
                .orElse(null);
    }
}
