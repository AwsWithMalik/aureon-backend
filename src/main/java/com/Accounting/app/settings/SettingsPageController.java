package com.Accounting.app.settings;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.Accounting.app.auth.Config;
import com.Accounting.app.settings.dto.ProfilePhotoContent;
import com.Accounting.app.settings.dto.SettingsPageResponse;

@RestController
public class SettingsPageController {
    private final Config config;
    private final SettingsPageServices settingsPageServices;

    public SettingsPageController(Config config, SettingsPageServices settingsPageServices) {
        this.config = config;
        this.settingsPageServices = settingsPageServices;
    }

    @GetMapping("/api/dashboard/settings")
    public ResponseEntity<SettingsPageResponse> getSettings() {
        return ResponseEntity.ok(settingsPageServices.settingsPageResponse(config.getEmail()));
    }

    @PutMapping("/api/dashboard/settings")
    public ResponseEntity<SettingsPageResponse> updateSettings(@RequestBody SettingsPageResponse request) {
        return ResponseEntity.ok(settingsPageServices.updateSettings(config.getEmail(), request));
    }

    @PostMapping("/api/dashboard/settings/profile-photo")
    public ResponseEntity<SettingsPageResponse> uploadProfilePhoto(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(settingsPageServices.updateProfilePhoto(config.getEmail(), file));
    }

    @GetMapping("/api/dashboard/settings/profile-photo/content")
    public ResponseEntity<Void> getProfilePhotoContent() {
        ProfilePhotoContent content = settingsPageServices.profilePhotoContent(config.getEmail());
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(content.redirectUri())
                .build();
    }
}
