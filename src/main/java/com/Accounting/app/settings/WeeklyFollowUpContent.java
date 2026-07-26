package com.Accounting.app.settings;

import java.util.List;

public record WeeklyFollowUpContent(
        String recipientEmail,
        String recipientName,
        String businessName,
        String periodLabel,
        List<WeeklyFollowUpSection> sections) {
}
