package com.murali.service;

import com.murali.entity.AuditLog;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;

@Service
public class AuditLogService {
    public List<AuditLog> getRecentLogs(int limit) {
        // TODO: Implement actual DB call
        return Collections.emptyList();
    }
}
