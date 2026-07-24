package com.campuscore.controller;

import com.campuscore.dto.ApiResponse;
import com.campuscore.entity.AuditLog;
import com.campuscore.entity.ModuleLog;
import com.campuscore.repository.AuditLogRepository;
import com.campuscore.repository.ModuleLogRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only endpoints that surface the audit_log and module_log tables.
 * Restricted to ADMIN so the frontend can render them on the admin page only.
 */
@Slf4j
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogController {

    private final AuditLogRepository auditLogRepository;
    private final ModuleLogRepository moduleLogRepository;

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditLogView>>> getAuditLogs() {
        log.info("ADMIN fetching audit logs");
        List<AuditLogView> rows = auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"))
                .stream().map(AuditLogView::from).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(rows, "Fetched audit logs"));
    }

    @GetMapping("/module")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ModuleLogView>>> getModuleLogs() {
        log.info("ADMIN fetching module logs");
        List<ModuleLogView> rows = moduleLogRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"))
                .stream().map(ModuleLogView::from).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(rows, "Fetched module logs"));
    }

    // ---- Flat, serialization-safe views (avoid lazy User proxies) ----

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AuditLogView {
        private Long auditId;
        private Long userId;
        private String userName;
        private String action;
        private String module;
        private LocalDateTime timestamp;

        static AuditLogView from(AuditLog a) {
            Long uid = null; String uname = null;
            try { if (a.getUser() != null) { uid = a.getUser().getUserId(); uname = a.getUser().getName(); } }
            catch (Exception ignored) {}
            return AuditLogView.builder()
                    .auditId(a.getAuditId())
                    .userId(uid).userName(uname)
                    .action(a.getAction())
                    .module(a.getModule())
                    .timestamp(a.getTimestamp())
                    .build();
        }
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ModuleLogView {
        private Long logId;
        private String moduleName;
        private String actionPerformed;
        private String accessedBy;
        private Long userId;
        private LocalDateTime timestamp;

        static ModuleLogView from(ModuleLog m) {
            Long uid = null;
            try { if (m.getUser() != null) uid = m.getUser().getUserId(); } catch (Exception ignored) {}
            return ModuleLogView.builder()
                    .logId(m.getLogId())
                    .moduleName(m.getModuleName())
                    .actionPerformed(m.getActionPerformed())
                    .accessedBy(m.getAccessedBy())
                    .userId(uid)
                    .timestamp(m.getTimestamp())
                    .build();
        }
    }
}
