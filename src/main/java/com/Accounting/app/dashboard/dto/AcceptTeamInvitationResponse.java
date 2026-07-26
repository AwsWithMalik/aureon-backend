package com.Accounting.app.dashboard.dto;

public record AcceptTeamInvitationResponse(
        TeamAccessPageResponse.Workspace workspace,
        TeamAccessPageResponse.Member member,
        TeamAccessPageResponse.Invitation invitation,
        String message) {
}
