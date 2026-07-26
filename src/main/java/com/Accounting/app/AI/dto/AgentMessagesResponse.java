package com.Accounting.app.AI.dto;

import java.util.List;

public record AgentMessagesResponse(
        SessionSummary session,
        List<MessageDto> messages) {
}
