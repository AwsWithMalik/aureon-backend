package com.Accounting.app.AI.dto;

public record SessionSummary(
        String id,
        String title,
        String preview,
        String updatedAt,
        String folderId,
        String folderName) {
}
