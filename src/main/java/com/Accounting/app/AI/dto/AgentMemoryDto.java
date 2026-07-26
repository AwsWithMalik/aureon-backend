package com.Accounting.app.AI.dto;

import java.time.LocalDateTime;

public record AgentMemoryDto(
        Integer id,
        String memoryType,
        String content,
        Double confidence,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
