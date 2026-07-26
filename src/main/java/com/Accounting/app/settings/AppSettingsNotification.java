package com.Accounting.app.settings;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppSettingsNotification {
    private String notificationId;
    private String label;
    private Boolean enabled;
}
