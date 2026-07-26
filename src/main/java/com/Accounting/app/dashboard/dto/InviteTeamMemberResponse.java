package com.Accounting.app.dashboard.dto;

import java.util.List;

public record InviteTeamMemberResponse(
        TeamAccessPageResponse.Invitation invitation,
        List<TeamAccessPageResponse.Member> members,
        List<TeamAccessPageResponse.Invitation> invitations,
        TeamInviteDelivery delivery) {
}
