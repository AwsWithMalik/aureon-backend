package com.Accounting.app.settings.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SettingsPageResponse {
    private ProfileSettings profile;
    private BusinessSettings business;
    private List<TeamMember> teamMembers;
    private List<NotificationSetting> notifications;
    private NotificationPreferencesSettings notificationPreferences;
    private SecuritySettings security;
    private BillingSettings billing;
}
