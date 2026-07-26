package com.Accounting.app.tax;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;

@RestController
public class TaxProfilePageController {
    private final Config config;
    private final TaxProfilePageServices taxProfilePageServices;

    public TaxProfilePageController(Config config, TaxProfilePageServices taxProfilePageServices) {
        this.config = config;
        this.taxProfilePageServices = taxProfilePageServices;
    }

    @GetMapping("/api/dashboard/tax-profile")
    public ResponseEntity<Map<String, Object>> getTaxProfile() {
        return ResponseEntity.ok(taxProfilePageServices.taxProfilePagePayload(config.getEmail()));
    }

    @PutMapping("/api/dashboard/tax-profile")
    public ResponseEntity<Map<String, Object>> updateTaxProfile(@RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.ok(taxProfilePageServices.updateTaxProfilePayload(config.getEmail(), request));
    }
}
