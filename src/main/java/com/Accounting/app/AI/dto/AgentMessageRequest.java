package com.Accounting.app.AI.dto;

public record AgentMessageRequest(
        String sessionId,
        String message) {
}
