package com.Accounting.app.dashboard.dto;

import java.util.List;

public record LinkedInstitutionsPageResponse(
        String periodLabel,
        Metrics metrics,
        List<InstitutionSummary> institutions,
        SelectedInstitution selectedInstitution) {

    public record Metrics(
            Metric connectedInstitutions,
            Metric healthyConnections,
            Metric needsAttention,
            Metric linkedAccounts,
            Metric lastSyncSuccessRate) {
    }

    public record Metric(
            int value,
            int change,
            String changeLabel) {
    }

    public record InstitutionSummary(
            String id,
            String name,
            String type,
            String logoUrl,
            String logoText,
            int linkedAccountCount,
            List<ProductEnabled> productsEnabled,
            String lastSyncedAt,
            String syncStatus) {
    }

    public record SelectedInstitution(
            String id,
            String name,
            String type,
            String logoUrl,
            String logoText,
            String syncStatus,
            List<LinkedAccount> linkedAccounts,
            List<ProductEnabled> productsEnabled,
            String lastSuccessfulSyncAt,
            String connectionStatus,
            String permissionSummary) {
    }

    public record LinkedAccount(
            String id,
            String name,
            String mask,
            String type,
            String subtype) {
    }

    public record ProductEnabled(
            String key,
            String label,
            boolean enabled) {
    }
}
