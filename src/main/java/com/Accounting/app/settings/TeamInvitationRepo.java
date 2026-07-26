package com.Accounting.app.settings;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamInvitationRepo extends JpaRepository<TeamInvitation, Integer> {
    List<TeamInvitation> findAllByWorkspaceEmailOrderByInvitedAtDesc(String workspaceEmail);

    Optional<TeamInvitation> findFirstByWorkspaceEmailAndInvitedEmailOrderByInvitedAtDesc(
            String workspaceEmail,
            String invitedEmail);

    Optional<TeamInvitation> findByInviteTokenHash(String inviteTokenHash);
}
