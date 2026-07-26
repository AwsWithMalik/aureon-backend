package com.Accounting.app.audit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.audit.dto.AuditLogPageResponse;
import com.Accounting.app.auth.Config;

@RestController
public class AuditLogPageController {
    private final Config config;
    private final AuditLogService auditLogService;

    public AuditLogPageController(Config config, AuditLogService auditLogService) {
        this.config = config;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/api/dashboard/audit-log")
    public ResponseEntity<AuditLogPageResponse> getAuditLog(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String actorType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String resource,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(auditLogService.auditLogPageResponse(
                config.getEmail(),
                query,
                actorType,
                result,
                resource,
                page,
                pageSize));
    }

    @GetMapping(value = "/api/dashboard/audit-log/export", produces = "text/csv")
    public ResponseEntity<String> exportAuditLog(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String actorType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String resource) {
        String csv = auditLogService.auditLogCsv(
                config.getEmail(),
                query,
                actorType,
                result,
                resource);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
