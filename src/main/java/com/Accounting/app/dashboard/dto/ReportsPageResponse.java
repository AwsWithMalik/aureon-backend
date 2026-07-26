package com.Accounting.app.dashboard.dto;

import java.util.List;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportsPageResponse {
    private List<MarginTrend> marginTrend;
    private List<KpiDelta> kpiDeltas;
    private List<SavedReport> savedReports;
    private List<ComplianceCalendar> complianceCalendar;
}
