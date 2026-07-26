package com.Accounting.app.AI.dto;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public record MessageDto(
        String id,
        String role,
        String content,
        String createdAt,
        String uploadRequest,
        String responseType,
        String answerType,
        String title,
        String summary,
        JsonNode sections,
        JsonNode blocks,
        JsonNode taxPlan,
        JsonNode meta,
        String reasoningSummary,
        String confidence,
        JsonNode missingInformation,
        JsonNode recommendedNextSteps,
        JsonNode sources,
        JsonNode usedContext,
        List<AgentUploadedFileDto> files) {
}
