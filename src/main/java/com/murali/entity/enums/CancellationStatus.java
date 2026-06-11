package com.murali.entity.enums;

public enum CancellationStatus {
    NONE,       // Default state for all normal leaves
    PENDING,    // Employee clicked cancel, waiting on approvers
    APPROVED,   // Approvers agreed, leave is officially cancelled
    REJECTED    // Approvers said no, original leave remains active
}


