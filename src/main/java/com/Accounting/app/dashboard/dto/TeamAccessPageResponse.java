package com.Accounting.app.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TeamAccessPageResponse(
        Workspace workspace,
        List<Member> members,
        List<Invitation> invitations) {

    public record Workspace(
            String id,
            String name) {
    }

    public record Member(
            String id,
            String name,
            String email,
            String role,
            String status,
            LocalDateTime lastActiveAt) {
    }

    public record Invitation(
            String id,
            String email,
            String role,
            String status,
            LocalDateTime invitedAt,
            LocalDateTime expiresAt) {
    }
}
