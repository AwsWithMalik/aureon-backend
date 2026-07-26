package com.Accounting.app.audit;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditLogFilter extends OncePerRequestFilter {
    private final AuditLogService auditLogService;

    public AuditLogFilter(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();

        if ("OPTIONS".equalsIgnoreCase(method)
                || path.startsWith("/api/dashboard/audit-log")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs")) {
            return true;
        }

        boolean auditablePath = path.startsWith("/api/dashboard/")
                || path.startsWith("/api/agent/")
                || path.startsWith("/link/token/create")
                || path.startsWith("/item/public_token/exchange")
                || path.startsWith("/api/transactions/sync");

        if (!auditablePath) {
            return true;
        }

        return "GET".equalsIgnoreCase(method) && !path.contains("/sync");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            String email = currentEmail();
            if (!email.isBlank()) {
                auditLogService.recordHttpAction(email, request, response.getStatus(), System.currentTimeMillis() - startedAt);
            }
        }
    }

    private String currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return "";
        }
        return authentication.getName();
    }
}
