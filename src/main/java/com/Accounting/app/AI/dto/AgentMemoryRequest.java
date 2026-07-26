package com.Accounting.app.AI.dto;

public record AgentMemoryRequest(
        String action,
        String memoryType,
        String content,
        Double confidence) {
}
