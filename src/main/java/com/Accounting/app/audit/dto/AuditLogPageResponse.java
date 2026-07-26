package com.Accounting.app.audit.dto;

import java.util.List;
import java.util.Map;

public record AuditLogPageResponse(
        Summary summary,
        Filters filters,
        List<Event> events,
        Pagination pagination) {
    public record Summary(
            long recordedActions,
            long adminActions,
            long failedActions,
            String retentionLabel) {
    }

    public record Filters(
            List<String> actorTypes,
            List<String> results,
            List<String> resources) {
    }

    public record Event(
            String id,
            String action,
            String detail,
            String actor,
            String actorType,
            String resource,
            String occurredAt,
            String ipAddress,
            String device,
            String result,
            String method,
            String path,
            Integer statusCode,
            String requestId,
            Map<String, Object> metadata) {
    }

    public record Pagination(
            int page,
            int pageSize,
            long totalItems,
            int totalPages) {
    }
}
