package com.Accounting.app.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamMember {
    private String id;
    private String name;
    private String email;
    private String role;
    private String status;
}
