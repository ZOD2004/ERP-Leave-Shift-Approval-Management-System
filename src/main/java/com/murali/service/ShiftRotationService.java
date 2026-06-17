package com.murali.service;

import com.murali.dto.ShiftAssignmentDTO;
import com.murali.entity.*;
import com.murali.entity.enums.RotationSegmentType;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.HolidayRepository;
import com.murali.repository.ShiftAssignmentRepository;
import com.murali.repository.ShiftRotationPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftRotationService {

    private final ShiftRotationPolicyRepository policyRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final ShiftAssignmentService shiftAssignmentService;
    private final EmployeeRepository employeeRepository;
    private final HolidayRepository holidayRepository;

    private static final int GENERATION_DAYS_AHEAD = 30;

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void dailyTopUpRotations() {
        log.info("Starting Daily Shift Rotation & Default Shift Top-Up Job.");

        List<Employee> activeEmployees = employeeRepository.findByActiveTrue();
        List<ShiftRotationPolicy> activePolicies = policyRepository.findByActiveTrue();

        Map<Long, ShiftRotationPolicy> policyMap = activePolicies.stream().collect(Collectors.toMap(p -> p.getEmployee().getId(), p -> p));

        LocalDate targetMinimumRunway = LocalDate.now().plusDays(GENERATION_DAYS_AHEAD);

        for (Employee employee : activeEmployees) {
            try {
                ShiftRotationPolicy policy = policyMap.get(employee.getId());

                if (policy != null) {
                    LocalDate startGenerationFrom = policy.getGeneratedUntil() != null ? policy.getGeneratedUntil().plusDays(1) : policy.getStartDate();

                    if (startGenerationFrom.isBefore(LocalDate.now().plusDays(7))) {
                        processPolicyForWindow(policy, startGenerationFrom, targetMinimumRunway);
                    }
                } else if (employee.getDefaultShift() != null) {
                    LocalDate startGenerationFrom = employee.getDefaultShiftGeneratedUntil() != null ? employee.getDefaultShiftGeneratedUntil().plusDays(1) : LocalDate.now();

                    if (startGenerationFrom.isBefore(LocalDate.now().plusDays(7))) {
                        gapFillDefaultShift(employee, startGenerationFrom, targetMinimumRunway);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process shift generation for Employee ID: {}", employee.getId(), e);
            }
        }
        log.info("Finished Daily Shift Rotation Top-Up Job.");
    }

    @Transactional
    public ShiftRotationPolicy createAndKickstartPolicy(ShiftRotationPolicy policy) {
        validatePolicy(policy);

        // Ensure starting state is clean
        policy.setGeneratedUntil(null);
        ShiftRotationPolicy savedPolicy = policyRepository.save(policy);

        LocalDate targetRunway = savedPolicy.getStartDate().plusDays(GENERATION_DAYS_AHEAD);
        processPolicyForWindow(savedPolicy, savedPolicy.getStartDate(), targetRunway);

        return savedPolicy;
    }

    private void processPolicyForWindow(ShiftRotationPolicy policy, LocalDate start, LocalDate targetMinimumRunway) {
        if (start.isAfter(targetMinimumRunway)) return;

        int totalCycleDays = calculateCycleDays(policy);

        // STRETCH TO FINISH LOOP: Calculate exact days needed to cleanly finish the N-day sequence
        long daysToRunway = ChronoUnit.DAYS.between(start, targetMinimumRunway);
        if (daysToRunway <= 0) daysToRunway = 1;
        int cyclesNeeded = (int) Math.ceil((double) daysToRunway / totalCycleDays);
        LocalDate actualEnd = start.plusDays((long) cyclesNeeded * totalCycleDays - 1);

        // Fetch existing assignments to respect HR overrides
        List<ShiftAssignment> existingAssignments = assignmentRepository.findByEmployeeIdInAndDateRange(List.of(policy.getEmployee().getId()), start, actualEnd);
        Set<LocalDate> occupiedDates = extractOccupiedDates(existingAssignments, start, actualEnd);
        Set<LocalDate> holidayDates = new HashSet<>(holidayRepository.findHolidayDatesBetween(start, actualEnd));

        List<ShiftAssignmentDTO> newAssignments = new ArrayList<>();
        Shift currentShift = null;
        LocalDate chunkStart = null;
        LocalDate chunkEnd = null;

        for (LocalDate date = start; !date.isAfter(actualEnd); date = date.plusDays(1)) {
            boolean isOccupied = occupiedDates.contains(date);
            RotationSequence activeSequence = calculateSequenceForDate(policy, date, totalCycleDays);

            boolean isOffDay = activeSequence == null || activeSequence.getSegmentType() == RotationSegmentType.OFF || holidayDates.contains(date);

            if (isOccupied || isOffDay) {
                // Break chunk and save
                if (currentShift != null) {
                    newAssignments.add(createAssignmentDTO(policy.getEmployee(), currentShift, chunkStart, chunkEnd));
                    currentShift = null;
                }
                continue;
            }

            Shift shiftForDay = activeSequence.getShift();

            // Chunking logic (combining consecutive matching days into one assignment)
            if (currentShift == null) {
                currentShift = shiftForDay;
                chunkStart = date;
                chunkEnd = date;
            } else if (!currentShift.getId().equals(shiftForDay.getId())) {
                newAssignments.add(createAssignmentDTO(policy.getEmployee(), currentShift, chunkStart, chunkEnd));
                currentShift = shiftForDay;
                chunkStart = date;
                chunkEnd = date;
            } else {
                chunkEnd = date; // Extend chunk
            }
        }

        if (currentShift != null) {
            newAssignments.add(createAssignmentDTO(policy.getEmployee(), currentShift, chunkStart, chunkEnd));
        }

        if (!newAssignments.isEmpty()) {
            shiftAssignmentService.saveResolvedBatch(newAssignments, false); // false = respect HR existing
        }

        // Advance the tracker so the cron job knows where to pick up next time
        policy.setGeneratedUntil(actualEnd);
        policyRepository.save(policy);

        log.info("Policy applied for Employee {} up to {}", policy.getEmployee().getId(), actualEnd);
    }

    private void gapFillDefaultShift(Employee employee, LocalDate start, LocalDate end) {
        List<ShiftAssignment> existingAssignments = assignmentRepository.findByEmployeeIdInAndDateRange(List.of(employee.getId()), start, end);
        Set<LocalDate> occupiedDates = extractOccupiedDates(existingAssignments, start, end);
        Set<LocalDate> holidayDates = new HashSet<>(holidayRepository.findHolidayDatesBetween(start, end));

        Shift defaultShift = employee.getDefaultShift();
        List<ShiftAssignmentDTO> newAssignments = new ArrayList<>();

        LocalDate chunkStart = null;
        LocalDate chunkEnd = null;
        boolean inChunk = false;

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String dayName = date.getDayOfWeek().name();
            boolean isWorkingDay = defaultShift.getWorkingDays().stream().anyMatch(wd -> wd.name().equalsIgnoreCase(dayName));

            if (occupiedDates.contains(date) || holidayDates.contains(date) || !isWorkingDay) {
                if (inChunk) {
                    newAssignments.add(createAssignmentDTO(employee, defaultShift, chunkStart, chunkEnd));
                    inChunk = false;
                }
                continue;
            }

            if (!inChunk) {
                chunkStart = date;
                chunkEnd = date;
                inChunk = true;
            } else {
                chunkEnd = date; // extend
            }
        }

        if (inChunk) {
            newAssignments.add(createAssignmentDTO(employee, defaultShift, chunkStart, chunkEnd));
        }

        if (!newAssignments.isEmpty()) {
            shiftAssignmentService.saveResolvedBatch(newAssignments, false); // false = respect HR overrides
            employee.setDefaultShiftGeneratedUntil(end);
            employeeRepository.save(employee);
        }
    }

    private ShiftAssignmentDTO createAssignmentDTO(Employee employee, Shift shift, LocalDate start, LocalDate end) {
        ShiftAssignmentDTO dto = new ShiftAssignmentDTO();
        dto.setEmployeeId(employee.getId());
        dto.setShiftId(shift.getId());
        dto.setStartDate(start);
        dto.setEndDate(end);
        return dto;
    }


    private void validatePolicy(ShiftRotationPolicy policy) {
        if (policy.getSequences() == null || policy.getSequences().isEmpty()) {
            throw new IllegalArgumentException("A rotation policy must have at least one sequence.");
        }

        for (RotationSequence seq : policy.getSequences()) {
            if (seq.getSegmentType() == RotationSegmentType.WORK && seq.getShift() == null) {
                throw new IllegalArgumentException("Sequence Order " + seq.getSequenceOrder() + " is marked as WORK but has no Shift assigned.");
            }
            if (seq.getSegmentType() == RotationSegmentType.OFF && seq.getShift() != null) {
                throw new IllegalArgumentException("Sequence Order " + seq.getSequenceOrder() + " is marked as OFF but has a Shift assigned.");
            }
            if (seq.getDurationDays() == null || seq.getDurationDays() <= 0) {
                throw new IllegalArgumentException("All sequences must have a duration greater than 0 days.");
            }
        }
    }

    public int calculateCycleDays(ShiftRotationPolicy policy) {
        return policy.getSequences().stream().mapToInt(RotationSequence::getDurationDays).sum();
    }


    public RotationSequence calculateSequenceForDate(ShiftRotationPolicy policy, LocalDate targetDate, int totalCycleDays) {
        long daysSinceStart = ChronoUnit.DAYS.between(policy.getStartDate(), targetDate);
        if (daysSinceStart < 0) return null;

        int cycleDay = (int) (daysSinceStart % totalCycleDays);

        for (RotationSequence seq : policy.getSequences()) {
            if (cycleDay < seq.getDurationDays()) {
                return seq;
            }
            cycleDay -= seq.getDurationDays();
        }
        return null;
    }

    private Set<LocalDate> extractOccupiedDates(List<ShiftAssignment> assignments, LocalDate startDate, LocalDate endDate) {
        Set<LocalDate> dates = new HashSet<>();
        for (ShiftAssignment sa : assignments) {
            LocalDate start = sa.getStartDate().isBefore(startDate) ? startDate : sa.getStartDate();
            LocalDate end = sa.getEndDate().isAfter(endDate) ? endDate : sa.getEndDate();
            while (!start.isAfter(end)) {
                dates.add(start);
                start = start.plusDays(1);
            }
        }
        return dates;
    }

    private ShiftAssignment createAssignment(Employee employee, Shift shift, LocalDate start, LocalDate end) {
        ShiftAssignment sa = new ShiftAssignment();
        sa.setEmployee(employee);
        sa.setShift(shift);
        sa.setStartDate(start);
        sa.setEndDate(end);
        return sa;
    }

    @Transactional(readOnly = true)
    public List<ShiftRotationPolicy> getAllPolicies() {
        return policyRepository.findAllWithEmployee();
    }


    @Transactional
    public void deletePolicy(Long id) {
        ShiftRotationPolicy policy = policyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Shift Rotation Policy not found with ID: " + id));

        policyRepository.delete(policy);

        log.info("Deleted Shift Rotation Policy ID: {}. Future shift generations for Employee ID: {} have been stopped.", id, policy.getEmployee().getId());
    }

    @Transactional(readOnly = true)
    public ShiftRotationPolicy getPolicyWithSequences(Long id) {
        return policyRepository.findByIdWithSequences(id).orElseThrow(() -> new IllegalArgumentException("Policy not found"));
    }
}