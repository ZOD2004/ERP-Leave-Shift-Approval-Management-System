package com.murali.service;

import com.murali.entity.AuditLog;
import com.murali.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public List<AuditLog> getRecentLogs(int limit) {
        return auditLogRepository.findByOrderByTimestampDesc(PageRequest.of(0, limit));
    }
}
