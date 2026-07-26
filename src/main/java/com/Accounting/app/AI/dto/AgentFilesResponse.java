package com.Accounting.app.AI.dto;

import java.util.List;

public record AgentFilesResponse(
        SessionSummary session,
        List<AgentUploadedFileDto> files,
        List<MessageDto> messages) {
}
