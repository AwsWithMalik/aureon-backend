package com.Accounting.app.plaid.dto;

public record InstitutionMetadataDto(
        String institutionId,
        String name,
        String logo,
        String primaryColor,
        String url) {
}
