package com.Accounting.app.dashboard;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.LinkedInstitutionsPageResponse;

@RestController
public class LinkedInstitutionsPageController {
    private final Config config;
    private final LinkedInstitutionsPageServices linkedInstitutionsPageServices;

    public LinkedInstitutionsPageController(
            Config config,
            LinkedInstitutionsPageServices linkedInstitutionsPageServices) {
        this.config = config;
        this.linkedInstitutionsPageServices = linkedInstitutionsPageServices;
    }

    @GetMapping("/api/dashboard/linked-institutions")
    public ResponseEntity<LinkedInstitutionsPageResponse> getLinkedInstitutions() {
        return ResponseEntity.ok(linkedInstitutionsPageServices.linkedInstitutionsPageResponse(config.getEmail()));
    }
}
