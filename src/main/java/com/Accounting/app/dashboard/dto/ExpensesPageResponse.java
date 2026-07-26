package com.Accounting.app.dashboard.dto;

import java.util.List;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpensesPageResponse {
    private List<SpendTrend> spendTrend;
    private List<Approval> approvals;
    private List<CategorySpend> categorySpend;
    private List<RecurringTool> recurringTools;
}
