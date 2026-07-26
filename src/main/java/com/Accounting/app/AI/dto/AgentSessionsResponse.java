package com.Accounting.app.AI.dto;

import java.util.List;

public record AgentSessionsResponse(
        List<SessionSummary> sessions,
        List<AgentFolderDto> folders) {
}
