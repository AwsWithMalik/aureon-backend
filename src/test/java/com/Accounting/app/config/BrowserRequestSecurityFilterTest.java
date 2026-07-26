package com.Accounting.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BrowserRequestSecurityFilterTest {
    private final BrowserRequestSecurityFilter filter = new BrowserRequestSecurityFilter(
            new AllowedOriginPolicy("https://crumbie.ca,http://localhost:5173"));

    @Test
    void permitsConfiguredOriginWithRequiredHeader() throws Exception {
        MockHttpServletRequest request = request("https://crumbie.ca");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void blocksUntrustedOrigin() throws Exception {
        MockHttpServletRequest request = request("https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void blocksSimpleCrossSiteFormWithoutCustomHeader() throws Exception {
        MockHttpServletRequest request = request("https://crumbie.ca");
        request.removeHeader("X-Requested-With");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    private MockHttpServletRequest request(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServerName("api.crumbie.ca");
        request.setScheme("https");
        request.addHeader("Origin", origin);
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        return request;
    }
}
