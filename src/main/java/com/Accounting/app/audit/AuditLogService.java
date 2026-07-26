package com.Accounting.app.audit;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Accounting.app.audit.dto.AuditLogPageResponse;
import com.Accounting.app.auth.User;
import com.Accounting.app.auth.UserRepo;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuditLogService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogEventRepo auditLogEventRepo;
    private final UserRepo userRepo;

    public AuditLogService(AuditLogEventRepo auditLogEventRepo, UserRepo userRepo) {
        this.auditLogEventRepo = auditLogEventRepo;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public AuditLogPageResponse auditLogPageResponse(
            String email,
            String query,
            String actorType,
            String result,
            String resource,
            int page,
            int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Specification<AuditLogEvent> filtered = filteredSpec(email, query, actorType, result, resource);
        Pageable pageable = PageRequest.of(
                safePage - 1,
                safePageSize,
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<AuditLogEvent> events = auditLogEventRepo.findAll(filtered, pageable);
        long totalEvents = auditLogEventRepo.count(userSpec(email));
        long adminEvents = auditLogEventRepo.count(userSpec(email).and(actorTypeSpec("Admin")));
        long failedEvents = auditLogEventRepo.count(userSpec(email).and(resultSpec("Failed")));

        return new AuditLogPageResponse(
                new AuditLogPageResponse.Summary(
                        totalEvents,
                        adminEvents,
                        failedEvents,
                        "Retained for the configured audit period"),
                new AuditLogPageResponse.Filters(
                        distinctOrDefault(auditLogEventRepo.findDistinctActorTypesByUserEmail(email), List.of("Customer", "Admin", "System")),
                        distinctOrDefault(auditLogEventRepo.findDistinctResultsByUserEmail(email), List.of("Success", "Failed")),
                        auditLogEventRepo.findDistinctResourcesByUserEmail(email)),
                events.getContent().stream().map(this::toDto).toList(),
                new AuditLogPageResponse.Pagination(
                        events.getNumber() + 1,
                        events.getSize(),
                        events.getTotalElements(),
                        Math.max(events.getTotalPages(), 1)));
    }

    @Transactional(readOnly = true)
    public String auditLogCsv(
            String email,
            String query,
            String actorType,
            String result,
            String resource) {
        Specification<AuditLogEvent> filtered = filteredSpec(email, query, actorType, result, resource);
        List<AuditLogEvent> events = auditLogEventRepo.findAll(filtered, Sort.by(Sort.Direction.DESC, "occurredAt"));

        StringBuilder csv = new StringBuilder();
        csv.append("id,occurredAt,actor,actorType,action,resource,result,ipAddress,device,method,path,statusCode,requestId,detail\n");
        for (AuditLogEvent event : events) {
            csv.append(csvCell(String.valueOf(event.getId()))).append(',')
                    .append(csvCell(event.getOccurredAt() == null ? "" : event.getOccurredAt().toString())).append(',')
                    .append(csvCell(event.getActor())).append(',')
                    .append(csvCell(event.getActorType())).append(',')
                    .append(csvCell(event.getAction())).append(',')
                    .append(csvCell(event.getResource())).append(',')
                    .append(csvCell(event.getResult())).append(',')
                    .append(csvCell(event.getIpAddress())).append(',')
                    .append(csvCell(event.getDevice())).append(',')
                    .append(csvCell(event.getMethod())).append(',')
                    .append(csvCell(event.getPath())).append(',')
                    .append(csvCell(event.getStatusCode() == null ? "" : event.getStatusCode().toString())).append(',')
                    .append(csvCell(event.getRequestId())).append(',')
                    .append(csvCell(event.getDetail()))
                    .append('\n');
        }
        return csv.toString();
    }

    @Transactional
    public void recordHttpAction(String email, HttpServletRequest request, int statusCode, long durationMs) {
        try {
            String path = requestPath(request);
            String action = actionFor(request.getMethod(), request.getServletPath());
            AuditLogEvent event = baseEvent(email, request);
            event.setAction(action);
            event.setDetail(action + " via " + request.getMethod() + " " + path + " returned " + statusCode + " in " + durationMs + "ms.");
            event.setResource(resourceFor(request.getServletPath()));
            event.setMethod(request.getMethod());
            event.setPath(path);
            event.setStatusCode(statusCode);
            event.setResult(statusCode >= 400 ? "Failed" : "Success");
            event.setMetadataJson("{\"durationMs\":" + durationMs + "}");
            auditLogEventRepo.save(event);
        } catch (RuntimeException ignored) {
            // Audit logging must never block the user action being recorded.
        }
    }

    @Transactional
    public void recordAuthenticationEvent(
            String email,
            String action,
            String detail,
            String result,
            HttpServletRequest request,
            int statusCode) {
        try {
            AuditLogEvent event = baseEvent(email, request);
            event.setAction(action);
            event.setDetail(detail);
            event.setResource("Authentication");
            event.setMethod(request.getMethod());
            event.setPath(requestPath(request));
            event.setStatusCode(statusCode);
            event.setResult(result);
            auditLogEventRepo.save(event);
        } catch (RuntimeException ignored) {
            // Authentication should not fail because audit persistence is unavailable.
        }
    }

    private AuditLogEvent baseEvent(String email, HttpServletRequest request) {
        String cleanedEmail = clean(email);
        Optional<User> user = cleanedEmail.isBlank() ? Optional.empty() : userRepo.findByEmail(cleanedEmail);
        AuditLogEvent event = new AuditLogEvent();
        event.setUser(user.orElse(null));
        event.setUserEmail(user.map(User::getEmail).orElse(cleanedEmail.isBlank() ? "unknown" : cleanedEmail));
        event.setActor(user.map(User::getEmail).orElse(cleanedEmail.isBlank() ? "Unknown actor" : cleanedEmail));
        event.setActorType("Customer");
        event.setOccurredAt(Instant.now());
        event.setIpAddress(clientIp(request));
        event.setDevice(clean(request.getHeader("User-Agent")));
        event.setRequestId(requestId(request));
        return event;
    }

    private AuditLogPageResponse.Event toDto(AuditLogEvent event) {
        return new AuditLogPageResponse.Event(
                event.getId() == null ? "" : event.getId().toString(),
                fallback(event.getAction(), "Recorded action"),
                fallback(event.getDetail(), ""),
                fallback(event.getActor(), event.getUserEmail()),
                fallback(event.getActorType(), "Customer"),
                fallback(event.getResource(), "Workspace"),
                event.getOccurredAt() == null ? "" : event.getOccurredAt().toString(),
                fallback(event.getIpAddress(), "Unavailable"),
                fallback(event.getDevice(), "Unavailable"),
                fallback(event.getResult(), "Success"),
                fallback(event.getMethod(), ""),
                fallback(event.getPath(), ""),
                event.getStatusCode(),
                fallback(event.getRequestId(), ""),
                event.getMetadataJson() == null || event.getMetadataJson().isBlank()
                        ? null
                        : java.util.Map.of("raw", event.getMetadataJson()));
    }

    private Specification<AuditLogEvent> filteredSpec(
            String email,
            String query,
            String actorType,
            String result,
            String resource) {
        Specification<AuditLogEvent> spec = userSpec(email);

        if (hasFilter(actorType, "All actors")) {
            spec = spec.and(actorTypeSpec(actorType));
        }
        if (hasFilter(result, "All results")) {
            spec = spec.and(resultSpec(result));
        }
        if (hasFilter(resource, "All resources")) {
            spec = spec.and(resourceSpec(resource));
        }
        if (query != null && !query.trim().isBlank()) {
            spec = spec.and(searchSpec(query.trim()));
        }

        return spec;
    }

    private Specification<AuditLogEvent> userSpec(String email) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(criteriaBuilder.lower(root.get("userEmail")), clean(email).toLowerCase(Locale.US));
    }

    private Specification<AuditLogEvent> actorTypeSpec(String actorType) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(criteriaBuilder.lower(root.get("actorType")), clean(actorType).toLowerCase(Locale.US));
    }

    private Specification<AuditLogEvent> resultSpec(String result) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(criteriaBuilder.lower(root.get("result")), clean(result).toLowerCase(Locale.US));
    }

    private Specification<AuditLogEvent> resourceSpec(String resource) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(criteriaBuilder.lower(root.get("resource")), clean(resource).toLowerCase(Locale.US));
    }

    private Specification<AuditLogEvent> searchSpec(String query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            String needle = "%" + query.toLowerCase(Locale.US) + "%";
            Predicate action = criteriaBuilder.like(criteriaBuilder.lower(root.get("action")), needle);
            Predicate detail = criteriaBuilder.like(criteriaBuilder.lower(root.get("detail")), needle);
            Predicate actor = criteriaBuilder.like(criteriaBuilder.lower(root.get("actor")), needle);
            Predicate resource = criteriaBuilder.like(criteriaBuilder.lower(root.get("resource")), needle);
            Predicate path = criteriaBuilder.like(criteriaBuilder.lower(root.get("path")), needle);
            Predicate requestId = criteriaBuilder.like(criteriaBuilder.lower(root.get("requestId")), needle);
            return criteriaBuilder.or(action, detail, actor, resource, path, requestId);
        };
    }

    private boolean hasFilter(String value, String allValue) {
        return value != null && !value.isBlank() && !value.equalsIgnoreCase(allValue);
    }

    private List<String> distinctOrDefault(List<String> values, List<String> fallback) {
        List<String> cleaned = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(Collectors.toList());
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private String actionFor(String method, String path) {
        String normalized = path.toLowerCase(Locale.US);

        if (normalized.contains("/auth/register")) {
            return "User registered";
        }
        if (normalized.contains("/auth/login")) {
            return "User signed in";
        }
        if (normalized.contains("/files/upload") || normalized.contains("/file/upload")) {
            return "Document uploaded";
        }
        if (normalized.contains("/settings")) {
            return "Settings updated";
        }
        if (normalized.contains("/tax-profile")) {
            return "Tax profile updated";
        }
        if (normalized.contains("/team-access/invitations")) {
            return "Team member invited";
        }
        if (normalized.contains("/transactions/") && "PATCH".equalsIgnoreCase(method)) {
            return "Transaction updated";
        }
        if (normalized.contains("/transactions/sync")) {
            return "Bank transactions synchronized";
        }
        if (normalized.contains("/link/token/create")) {
            return "Plaid link token created";
        }
        if (normalized.contains("/item/public_token/exchange")) {
            return "Bank institution connected";
        }
        if (normalized.contains("/agent/files")) {
            return "Agent file uploaded";
        }
        if (normalized.contains("/agent/messages")) {
            return "Agent message sent";
        }
        if (normalized.contains("/agent/sessions")) {
            return "Agent session updated";
        }

        return method + " " + path;
    }

    private String resourceFor(String path) {
        String normalized = path.toLowerCase(Locale.US);
        if (normalized.contains("/auth/")) {
            return "Authentication";
        }
        if (normalized.contains("/files") || normalized.contains("/receipts") || normalized.contains("/spreadsheets")) {
            return "Documents";
        }
        if (normalized.contains("/settings")) {
            return "Settings";
        }
        if (normalized.contains("/tax-profile")) {
            return "Tax profile";
        }
        if (normalized.contains("/team-access")) {
            return "Team access";
        }
        if (normalized.contains("/transactions")) {
            return "Transactions";
        }
        if (normalized.contains("/link/") || normalized.contains("/item/") || normalized.contains("/accounts")) {
            return "Banking";
        }
        if (normalized.contains("/agent")) {
            return "AI Workspace";
        }
        return "Workspace";
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = clean(request.getHeader("X-Forwarded-For"));
        if (!forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return fallback(request.getRemoteAddr(), "Unavailable");
    }

    private String requestPath(HttpServletRequest request) {
        String path = request.getServletPath();
        String query = request.getQueryString();
        return query == null || query.isBlank() ? path : path + "?" + query;
    }

    private String requestId(HttpServletRequest request) {
        String requestId = clean(request.getHeader("X-Request-ID"));
        return requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }

    private String csvCell(String value) {
        String safe = fallback(value, "");
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
