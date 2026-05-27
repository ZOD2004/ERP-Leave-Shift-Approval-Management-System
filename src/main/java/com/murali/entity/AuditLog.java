package com.murali.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ADD THIS BACK IN
    @Column(name = "record_id")
    private Long recordId;

    @Column(name = "timestamp")
    @CreationTimestamp
    private LocalDateTime timestamp;

    @Column(name = "action", length = 20)
    private String action; // CREATE, UPDATE, DELETE

    @Column(name = "entity_name", length = 100)
    private String entityName; // e.g., "LeaveBalance", "ShiftAssignment"

    @Column(name = "performed_by")
    private String performedBy;

    @Column(name = "old_state", columnDefinition = "text")
    private String oldState;

    @Column(name = "new_state", columnDefinition = "text")
    private String newState;
}