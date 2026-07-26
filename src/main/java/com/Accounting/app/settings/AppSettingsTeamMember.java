package com.Accounting.app.settings;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppSettingsTeamMember {
    private String memberId;
    private String name;
    private String email;
    private String role;
    private String status;
}
