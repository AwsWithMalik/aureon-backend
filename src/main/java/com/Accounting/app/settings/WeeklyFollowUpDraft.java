package com.Accounting.app.settings;

import java.util.List;

public record WeeklyFollowUpDraft(
        String subject,
        String preview,
        String headline,
        String summary,
        List<WeeklyFollowUpDraftSection> sections,
        List<String> recommendedActions,
        String plainTextBody,
        boolean aiGenerated,
        String fallbackReason) {
}
