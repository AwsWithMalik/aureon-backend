package com.Accounting.app.settings;

import java.util.List;

public record WeeklyFollowUpDraftSection(
        String title,
        String body,
        List<String> items,
        String tone) {
}
