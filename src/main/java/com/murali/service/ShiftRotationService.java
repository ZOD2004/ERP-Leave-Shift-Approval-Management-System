package com.murali.service;

import com.murali.entity.*;
import com.murali.entity.enums.RotationSegmentType;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftRotationService {

    private final ShiftRotationPolicyRepository policyRepository;
    private final ShiftAssignmentRepository assignmentRepository;

    private static final int GENERATION_DAYS_AHEAD = 30;


    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void dailyTopUpRotations() {
        log.info("Starting Daily Shift Rotation Top-Up Job.");

        List<ShiftRotationPolicy> activePolicies = policyRepository.findByActiveTrue();
        LocalDate targetMinimumRunway = LocalDate.now().plusDays(GENERATION_DAYS_AHEAD);

        for (ShiftRotationPolicy policy : activePolicies) {
            try {
                LocalDate maxGeneratedDate = assignmentRepository.findMaxEndDateByEmployeeId(policy.getEmployee().getId())
                        .orElse(policy.getStartDate().minusDays(1));

                if (maxGeneratedDate.isBefore(LocalDate.now().plusDays(7))) {

                    LocalDate generationEnd = targetMinimumRunway;
                    if (policy.getEndDate() != null && generationEnd.isAfter(policy.getEndDate())) {
                        generationEnd = policy.getEndDate();
                    }

                    if (!maxGeneratedDate.isBefore(generationEnd)) {
                        continue;
                    }

                    LocalDate startGenerationFrom = maxGeneratedDate.plusDays(1);
                    if(startGenerationFrom.isBefore(policy.getStartDate())){
                        startGenerationFrom = policy.getStartDate();
                    }

                    processPolicyForWindow(policy, startGenerationFrom, generationEnd);
                }
            } catch (Exception e) {
                log.error("Failed to process Shift Rotation Policy ID: {}", policy.getId(), e);
            }
        }
        log.info("Finished Daily Shift Rotation Top-Up Job.");
    }

    @Transactional
    public ShiftRotationPolicy createAndKickstartPolicy(ShiftRotationPolicy policy) {
        validatePolicy(policy);
        ShiftRotationPolicy savedPolicy = policyRepository.save(policy);

        LocalDate generationEnd = savedPolicy.getStartDate().plusDays(GENERATION_DAYS_AHEAD);
        if (savedPolicy.getEndDate() != null && generationEnd.isAfter(savedPolicy.getEndDate())) {
            generationEnd = savedPolicy.getEndDate();
        }

        processPolicyForWindow(savedPolicy, savedPolicy.getStartDate(), generationEnd);
        return savedPolicy;
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

    public void processPolicyForWindow(ShiftRotationPolicy policy, LocalDate start, LocalDate end) {
        if (start.isAfter(end)) return;

        int totalCycleDays = calculateCycleDays(policy);

        List<ShiftAssignment> existingAssignments = assignmentRepository.findByEmployeeIdInAndDateRange(
                List.of(policy.getEmployee().getId()), start, end);
        Set<LocalDate> occupiedDates = extractOccupiedDates(existingAssignments, start, end);

        List<ShiftAssignment> newAssignments = new ArrayList<>();
        Shift currentShift = null;
        LocalDate chunkStart = null;
        LocalDate chunkEnd = null;

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (occupiedDates.contains(date)) {
                if (currentShift != null) {
                    newAssignments.add(createAssignment(policy.getEmployee(), currentShift, chunkStart, chunkEnd));
                    currentShift = null;
                }
                continue;
            }

            RotationSequence activeSequence = calculateSequenceForDate(policy, date, totalCycleDays);

           if (activeSequence == null || activeSequence.getSegmentType() == RotationSegmentType.OFF) {
                if (currentShift != null) {
                    newAssignments.add(createAssignment(policy.getEmployee(), currentShift, chunkStart, chunkEnd));
                    currentShift = null;
                }
                continue;
            }

            Shift shiftForDay = activeSequence.getShift();

            if (currentShift == null) {
                currentShift = shiftForDay;
                chunkStart = date;
                chunkEnd = date;
            } else if (!currentShift.getId().equals(shiftForDay.getId())) {
                newAssignments.add(createAssignment(policy.getEmployee(), currentShift, chunkStart, chunkEnd));
                currentShift = shiftForDay;
                chunkStart = date;
                chunkEnd = date;
            } else {
                chunkEnd = date;
            }
        }

        if (currentShift != null) {
            newAssignments.add(createAssignment(policy.getEmployee(), currentShift, chunkStart, chunkEnd));
        }

        if (!newAssignments.isEmpty()) {
            assignmentRepository.saveAll(newAssignments);
            log.info("Generated {} chunked assignment records for Employee {} from {} to {}",
                    newAssignments.size(), policy.getEmployee().getId(), start, end);
        }
    }

    public int calculateCycleDays(ShiftRotationPolicy policy) {
        return policy.getSequences().stream()
                .mapToInt(RotationSequence::getDurationDays)
                .sum();
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
        ShiftRotationPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shift Rotation Policy not found with ID: " + id));

        policyRepository.delete(policy);

        log.info("Deleted Shift Rotation Policy ID: {}. Future shift generations for Employee ID: {} have been stopped.",
                id, policy.getEmployee().getId());
    }
    @Transactional(readOnly = true)
    public ShiftRotationPolicy getPolicyWithSequences(Long id) {
        return policyRepository.findByIdWithSequences(id)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found"));
    }
}