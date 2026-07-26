package com.Accounting.app.dashboard;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.dashboard.dto.AcceptTeamInvitationRequest;
import com.Accounting.app.dashboard.dto.AcceptTeamInvitationResponse;
import com.Accounting.app.dashboard.dto.TeamInvitationLookupResponse;

@RestController
@RequestMapping("/api/team-invitations")
public class TeamInvitationPublicController {
    private final TeamAccessPageServices teamAccessPageServices;

    public TeamInvitationPublicController(TeamAccessPageServices teamAccessPageServices) {
        this.teamAccessPageServices = teamAccessPageServices;
    }

    @GetMapping("/{token}")
    public ResponseEntity<TeamInvitationLookupResponse> invitation(@PathVariable String token) {
        return ResponseEntity.ok(teamAccessPageServices.invitationByToken(token));
    }

    @PostMapping("/{token}/accept")
    public ResponseEntity<AcceptTeamInvitationResponse> acceptInvitation(
            @PathVariable String token,
            @RequestBody(required = false) AcceptTeamInvitationRequest request) {
        return ResponseEntity.ok(teamAccessPageServices.acceptInvitation(token, request));
    }
}
