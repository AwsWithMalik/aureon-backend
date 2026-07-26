package com.Accounting.app.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCalendar {
    private String id;
    private String event;
    private String dueDate;
    private String status;
}
