package com.Accounting.app.dashboard.dto;

public record InviteTeamMemberRequest(
        String email,
        String role,
        String message) {
}
