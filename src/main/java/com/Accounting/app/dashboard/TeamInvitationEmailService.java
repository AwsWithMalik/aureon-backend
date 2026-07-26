package com.Accounting.app.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.Accounting.app.dashboard.dto.TeamInviteDelivery;
import com.Accounting.app.settings.TeamInvitation;

@Service
public class TeamInvitationEmailService {
    private static final Logger log = LoggerFactory.getLogger(TeamInvitationEmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String frontendUrl;
    private final String acceptPath;
    private final boolean mailEnabled;
    private final String fromAddress;

    public TeamInvitationEmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            @Value("${app.team-invite.accept-path:/team-invite}") String acceptPath,
            @Value("${app.team-invite.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.team-invite.mail.from:no-reply@ledger-luxe.local}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.frontendUrl = trimTrailingSlash(frontendUrl);
        this.acceptPath = normalizePath(acceptPath);
        this.mailEnabled = mailEnabled;
        this.fromAddress = fromAddress;
    }

    public TeamInviteDelivery sendInvitation(TeamInvitation invitation, String inviterName, String rawToken) {
        String inviteUrl = inviteUrl(rawToken);
        if (!mailEnabled) {
            log.info("Team invite email disabled. Invite URL for {}: {}", invitation.getInvitedEmail(), inviteUrl);
            return new TeamInviteDelivery("disabled", inviteUrl, "Email delivery is disabled; invite URL was logged.");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Team invite email enabled but JavaMailSender is not configured. Invite URL for {}: {}",
                    invitation.getInvitedEmail(),
                    inviteUrl);
            return new TeamInviteDelivery("not_configured", inviteUrl, "Email sender is not configured.");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(invitation.getInvitedEmail());
            message.setSubject("You're invited to join " + safeWorkspaceName(inviterName) + " on Ledger Luxe");
            message.setText(emailBody(invitation, inviterName, inviteUrl));
            mailSender.send(message);
            return new TeamInviteDelivery("sent", inviteUrl, "Invitation email sent.");
        } catch (MailException ex) {
            log.warn("Failed to send team invitation email to {}: {}", invitation.getInvitedEmail(), ex.getMessage());
            return new TeamInviteDelivery("failed", inviteUrl, ex.getMessage());
        }
    }

    private String inviteUrl(String rawToken) {
        return frontendUrl + acceptPath + "?token=" + rawToken;
    }

    private String emailBody(TeamInvitation invitation, String inviterName, String inviteUrl) {
        String workspaceName = safeWorkspaceName(inviterName);
        StringBuilder body = new StringBuilder();
        body.append("You were invited to join ")
                .append(workspaceName)
                .append(" on Ledger Luxe as ")
                .append(invitation.getRole())
                .append(".\n\n");

        if (invitation.getMessage() != null && !invitation.getMessage().isBlank()) {
            body.append(invitation.getMessage().trim()).append("\n\n");
        }

        body.append("Accept the invitation:\n")
                .append(inviteUrl)
                .append("\n\nThis invitation expires at ")
                .append(invitation.getExpiresAt())
                .append(".");
        return body.toString();
    }

    private String safeWorkspaceName(String inviterName) {
        return inviterName != null && !inviterName.isBlank() ? inviterName.trim() : "a workspace";
    }

    private String trimTrailingSlash(String value) {
        String cleaned = value == null || value.isBlank() ? "http://localhost:5173" : value.trim();
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private String normalizePath(String value) {
        String cleaned = value == null || value.isBlank() ? "/team-invite" : value.trim();
        return cleaned.startsWith("/") ? cleaned : "/" + cleaned;
    }
}
