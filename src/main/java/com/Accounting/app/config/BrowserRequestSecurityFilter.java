package com.Accounting.app.config;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.Accounting.app.auth.CookieUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class BrowserRequestSecurityFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final AllowedOriginPolicy originPolicy;

    public BrowserRequestSecurityFilter(AllowedOriginPolicy originPolicy) {
        this.originPolicy = originPolicy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SAFE_METHODS.contains(request.getMethod())
                || "/api/plaid/webhook".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if ((origin == null || origin.isBlank()) && request.getHeader(HttpHeaders.REFERER) != null) {
            origin = originFromReferer(request.getHeader(HttpHeaders.REFERER));
        }

        if ((origin == null || origin.isBlank()) && isBearerOnlyRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean requestedWith = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (!requestedWith || origin == null || !originPolicy.isAllowed(request, origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-site request blocked");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBearerOnlyRequest(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null
                && authorization.startsWith("Bearer ")
                && !hasBrowserAuthCookie(request);
    }

    private boolean hasBrowserAuthCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (CookieUtil.ACCESS_TOKEN_COOKIE.equals(cookie.getName())
                    || CookieUtil.REFRESH_TOKEN_COOKIE.equals(cookie.getName())
                    || CookieUtil.MFA_TOKEN_COOKIE.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private String originFromReferer(String referer) {
        try {
            URI uri = URI.create(referer);
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }
}
