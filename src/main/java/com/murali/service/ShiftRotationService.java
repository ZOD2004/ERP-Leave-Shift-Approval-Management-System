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

    private static final int GENERATION_BUFFER_DAYS = 30;

    /**
     * The Daily Top-Up Cron Job. Runs at 2:00 AM every night.
     * Looks for active policies whose generated runway is running out.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void dailyTopUpRotations() {
        log.info("Starting Daily Shift Rotation Top-Up Job.");

        List<ShiftRotationPolicy> activePolicies = policyRepository.findByActiveTrue();
        LocalDate targetMinimumRunway = LocalDate.now().plusDays(GENERATION_BUFFER_DAYS);

        for (ShiftRotationPolicy policy : activePolicies) {
            try {
                // Find the latest shift assignment generated for this policy's employee
                LocalDate maxGeneratedDate = assignmentRepository.findMaxEndDateByEmployeeId(policy.getEmployee().getId())
                        .orElse(policy.getStartDate().minusDays(1)); // Fallback to before policy started

                // If runway is expiring within 7 days, top it up to the 30-day buffer
                if (maxGeneratedDate.isBefore(LocalDate.now().plusDays(7))) {

                    // Don't generate past the policy's end date (if it has one)
                    LocalDate generationEnd = targetMinimumRunway;
                    if (policy.getEndDate() != null && generationEnd.isAfter(policy.getEndDate())) {
                        generationEnd = policy.getEndDate();
                    }

                    if (!maxGeneratedDate.isBefore(generationEnd)) {
                        continue; // No generation needed, already past end date
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

    /**
     * Called by the API when HR creates a brand new policy.
     * Validates the policy, saves it, and immediately kickstarts the first 30 days of shifts.
     */
    @Transactional
    public ShiftRotationPolicy createAndKickstartPolicy(ShiftRotationPolicy policy) {
        validatePolicy(policy);
        ShiftRotationPolicy savedPolicy = policyRepository.save(policy);

        LocalDate generationEnd = savedPolicy.getStartDate().plusDays(GENERATION_BUFFER_DAYS);
        if (savedPolicy.getEndDate() != null && generationEnd.isAfter(savedPolicy.getEndDate())) {
            generationEnd = savedPolicy.getEndDate();
        }

        processPolicyForWindow(savedPolicy, savedPolicy.getStartDate(), generationEnd);
        return savedPolicy;
    }

    /**
     * Validates that WORK segments have shifts, OFF segments don't, and sequences are ordered.
     */
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

    /**
     * Processes a single policy, evaluates segment types, skips manual conflicts, and chunks the result.
     */
    public void processPolicyForWindow(ShiftRotationPolicy policy, LocalDate windowStart, LocalDate windowEnd) {
        if (windowStart.isAfter(windowEnd)) return;

        int totalCycleDays = calculateCycleDays(policy);

        // Fetch existing assignments to map out "Occupied Dates" (Manual Overrides by HR)
        List<ShiftAssignment> existingAssignments = assignmentRepository.findByEmployeeIdInAndDateRange(
                List.of(policy.getEmployee().getId()), windowStart, windowEnd
        );
        Set<LocalDate> occupiedDates = extractOccupiedDates(existingAssignments, windowStart, windowEnd);

        List<ShiftAssignment> newAssignments = new ArrayList<>();
        Shift currentShift = null;
        LocalDate chunkStart = null;
        LocalDate chunkEnd = null;

        for (LocalDate date = windowStart; !date.isAfter(windowEnd); date = date.plusDays(1)) {

            // If HR manually assigned a shift here, we skip it and break the current chunk
            if (occupiedDates.contains(date)) {
                if (currentShift != null) {
                    newAssignments.add(createAssignment(policy.getEmployee(), currentShift, chunkStart, chunkEnd));
                    currentShift = null;
                }
                continue;
            }

            RotationSequence activeSequence = calculateSequenceForDate(policy, date, totalCycleDays);

            // If it's an OFF segment, we close the chunk and do NOT assign a shift for this day
            if (activeSequence == null || activeSequence.getSegmentType() == RotationSegmentType.OFF) {
                if (currentShift != null) {
                    newAssignments.add(createAssignment(policy.getEmployee(), currentShift, chunkStart, chunkEnd));
                    currentShift = null;
                }
                continue;
            }

            // It's a WORK segment! Let's group it.
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

        // Close out the final pending chunk
        if (currentShift != null) {
            newAssignments.add(createAssignment(policy.getEmployee(), currentShift, chunkStart, chunkEnd));
        }

        if (!newAssignments.isEmpty()) {
            assignmentRepository.saveAll(newAssignments);
            log.debug("Generated {} chunked assignment records for Employee {} from {} to {}",
                    newAssignments.size(), policy.getEmployee().getId(), windowStart, windowEnd);
        }
    }

    /**
     * Dynamically calculates total cycle length.
     */
    private int calculateCycleDays(ShiftRotationPolicy policy) {
        return policy.getSequences().stream()
                .mapToInt(RotationSequence::getDurationDays)
                .sum();
    }

    /**
     * Mathematical engine to find exactly which RotationSequence falls on a specific date.
     */
    private RotationSequence calculateSequenceForDate(ShiftRotationPolicy policy, LocalDate targetDate, int totalCycleDays) {
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

    private Set<LocalDate> extractOccupiedDates(List<ShiftAssignment> assignments, LocalDate windowStart, LocalDate windowEnd) {
        Set<LocalDate> dates = new HashSet<>();
        for (ShiftAssignment sa : assignments) {
            LocalDate cur = sa.getStartDate().isBefore(windowStart) ? windowStart : sa.getStartDate();
            LocalDate end = sa.getEndDate().isAfter(windowEnd) ? windowEnd : sa.getEndDate();
            while (!cur.isAfter(end)) {
                dates.add(cur);
                cur = cur.plusDays(1);
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

    /**
     * Deletes a shift rotation policy.
     * Note: This stops future generation, but does NOT delete already-generated shifts.
     */
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