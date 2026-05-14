package com.murali.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BatchPreviewResponse {

    private List<ShiftAssignmentDTO> readyToSave = new ArrayList<>();

    private List<ShiftConflictDTO> hardConflicts = new ArrayList<>();

    private List<ShiftConflictDTO> partialConflicts = new ArrayList<>();

}
