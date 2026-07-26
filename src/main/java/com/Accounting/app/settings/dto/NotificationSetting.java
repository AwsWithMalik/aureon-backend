package com.Accounting.app.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSetting {
    private String id;
    private String label;
    private Boolean enabled;
}
