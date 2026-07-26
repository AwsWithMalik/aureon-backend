package com.Accounting.app.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.PortfolioOverviewPageResponse;

@RestController
public class PortfolioOverviewPageController {
    private final Config config;
    private final PortfolioOverviewPageServices portfolioOverviewPageServices;

    public PortfolioOverviewPageController(Config config, PortfolioOverviewPageServices portfolioOverviewPageServices) {
        this.config = config;
        this.portfolioOverviewPageServices = portfolioOverviewPageServices;
    }

    @GetMapping("/api/dashboard/portfolio-overview")
    public ResponseEntity<PortfolioOverviewPageResponse> getPortfolioOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "all") String accountScope) {
        return ResponseEntity.ok(portfolioOverviewPageServices.portfolioOverviewPageResponse(
                config.getEmail(),
                from,
                to,
                accountScope));
    }
}
