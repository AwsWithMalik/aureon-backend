package com.Accounting.app.dashboard.dto;

public record TeamInvitationLookupResponse(
        TeamAccessPageResponse.Workspace workspace,
        TeamAccessPageResponse.Invitation invitation,
        String invitedEmail,
        String message) {
}
