package com.Accounting.app.AI.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentUploadedFileDto(
        String id,
        String fileName,
        String fileType,
        String mimeType,
        Long sizeBytes,
        String status,
        String createdAt,
        String uploadRequest,
        JsonNode extractedData) {
}
