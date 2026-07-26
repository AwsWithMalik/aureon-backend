package com.Accounting.app.AI.dto;

import java.util.List;

public record SessionDetail(
        String id,
        String title,
        String preview,
        String updatedAt,
        String folderId,
        String folderName,
        List<MessageDto> messages) {
}
