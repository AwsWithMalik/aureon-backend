package com.Accounting.app.dashboard;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Accounting.app.auth.Config;
import com.Accounting.app.dashboard.dto.InviteTeamMemberRequest;
import com.Accounting.app.dashboard.dto.InviteTeamMemberResponse;
import com.Accounting.app.dashboard.dto.TeamAccessPageResponse;

@RestController
@RequestMapping("/api/dashboard/team-access")
public class TeamAccessPageController {
    private final Config config;
    private final TeamAccessPageServices teamAccessPageServices;

    public TeamAccessPageController(Config config, TeamAccessPageServices teamAccessPageServices) {
        this.config = config;
        this.teamAccessPageServices = teamAccessPageServices;
    }

    @GetMapping
    public ResponseEntity<TeamAccessPageResponse> getTeamAccess() {
        return ResponseEntity.ok(teamAccessPageServices.teamAccessPageResponse(config.getEmail()));
    }

    @PostMapping("/invitations")
    public ResponseEntity<InviteTeamMemberResponse> inviteTeamMember(@RequestBody InviteTeamMemberRequest request) {
        return ResponseEntity.ok(teamAccessPageServices.inviteTeamMember(config.getEmail(), request));
    }
}
