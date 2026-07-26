package com.Accounting.app.dashboard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Accounting.app.auth.User;
import com.Accounting.app.auth.UserRepo;
import com.Accounting.app.dashboard.dto.AcceptTeamInvitationRequest;
import com.Accounting.app.dashboard.dto.AcceptTeamInvitationResponse;
import com.Accounting.app.dashboard.dto.InviteTeamMemberRequest;
import com.Accounting.app.dashboard.dto.InviteTeamMemberResponse;
import com.Accounting.app.dashboard.dto.TeamAccessPageResponse;
import com.Accounting.app.dashboard.dto.TeamInvitationLookupResponse;
import com.Accounting.app.dashboard.dto.TeamInviteDelivery;
import com.Accounting.app.exceptions.InvalidInputException;
import com.Accounting.app.exceptions.UserNotFoundException;
import com.Accounting.app.settings.AppSettings;
import com.Accounting.app.settings.AppSettingsRepo;
import com.Accounting.app.settings.AppSettingsTeamMember;
import com.Accounting.app.settings.TeamInvitation;
import com.Accounting.app.settings.TeamInvitationRepo;

@Service
public class TeamAccessPageServices {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String DEFAULT_STATUS = "active";
    private static final String DEFAULT_ROLE = "member";
    private static final Set<String> ALLOWED_ROLES = Set.of("owner", "admin", "accountant", "member", "viewer");
    private static final Set<String> DISABLED_MEMBER_STATUSES = Set.of("disabled", "inactive", "revoked");
    private static final Set<String> ACTIVE_MEMBER_STATUSES = Set.of("active", "accepted");
    private static final Set<String> CLOSED_INVITATION_STATUSES = Set.of("accepted", "revoked", "expired");

    private final UserRepo userRepo;
    private final AppSettingsRepo appSettingsRepo;
    private final TeamInvitationRepo teamInvitationRepo;
    private final TeamInvitationEmailService teamInvitationEmailService;

    public TeamAccessPageServices(
            UserRepo userRepo,
            AppSettingsRepo appSettingsRepo,
            TeamInvitationRepo teamInvitationRepo,
            TeamInvitationEmailService teamInvitationEmailService) {
        this.userRepo = userRepo;
        this.appSettingsRepo = appSettingsRepo;
        this.teamInvitationRepo = teamInvitationRepo;
        this.teamInvitationEmailService = teamInvitationEmailService;
    }

    @Transactional
    public TeamAccessPageResponse teamAccessPageResponse(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        AppSettings settings = appSettingsRepo.findByEmail(email)
                .orElseGet(() -> appSettingsRepo.save(defaultSettings(user)));
        List<TeamInvitation> invitations = teamInvitationRepo.findAllByWorkspaceEmailOrderByInvitedAtDesc(email);

        return new TeamAccessPageResponse(
                workspace(settings, user),
                members(settings, user, invitations),
                invitations(invitations));
    }

    @Transactional
    public InviteTeamMemberResponse inviteTeamMember(String email, InviteTeamMemberRequest request) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        AppSettings settings = appSettingsRepo.findByEmail(email)
                .orElseGet(() -> appSettingsRepo.save(defaultSettings(user)));

        String invitedEmail = normalizeEmail(request.email());
        if (invitedEmail == null) {
            throw new InvalidInputException("Invitation email is required");
        }
        if (!invitedEmail.contains("@")) {
            throw new InvalidInputException("Invitation email is invalid");
        }
        if (invitedEmail.equalsIgnoreCase(email)) {
            throw new InvalidInputException("You cannot invite yourself");
        }

        TeamInvitation invitation = teamInvitationRepo
                .findFirstByWorkspaceEmailAndInvitedEmailOrderByInvitedAtDesc(email, invitedEmail)
                .orElseGet(TeamInvitation::new);

        LocalDateTime now = LocalDateTime.now();
        TokenPair tokenPair = newTokenPair();
        invitation.setWorkspaceEmail(email);
        invitation.setInvitedEmail(invitedEmail);
        invitation.setRole(normalizeRole(request.role()));
        invitation.setStatus("pending");
        invitation.setInviteTokenHash(tokenPair.hash());
        invitation.setInvitedAt(now);
        invitation.setExpiresAt(now.plusDays(7));
        invitation.setAcceptedAt(null);
        invitation.setMessage(clean(request.message()));

        TeamInvitation savedInvitation = teamInvitationRepo.save(invitation);
        upsertTeamMember(settings, invitedEmail, null, savedInvitation.getRole(), "invited");

        TeamInviteDelivery delivery = teamInvitationEmailService.sendInvitation(
                savedInvitation,
                fallback(settings.getBusinessName(), fallback(user.getName(), user.getEmail())),
                tokenPair.raw());
        savedInvitation.setEmailDeliveryStatus(delivery.status());
        savedInvitation.setEmailDeliveryError(truncate(delivery.message(), 1000));
        if ("sent".equals(delivery.status())) {
            savedInvitation.setEmailSentAt(now);
        }
        teamInvitationRepo.save(savedInvitation);

        return new InviteTeamMemberResponse(
                invitation(savedInvitation),
                members(settings, user, teamInvitationRepo.findAllByWorkspaceEmailOrderByInvitedAtDesc(email)),
                invitations(email),
                delivery);
    }

    @Transactional
    public TeamInvitationLookupResponse invitationByToken(String token) {
        TeamInvitation invitation = invitationFromToken(token);
        User owner = userRepo.findByEmail(invitation.getWorkspaceEmail())
                .orElseThrow(() -> new UserNotFoundException("Workspace owner not found"));
        AppSettings settings = appSettingsRepo.findByEmail(owner.getEmail())
                .orElseGet(() -> appSettingsRepo.save(defaultSettings(owner)));

        return new TeamInvitationLookupResponse(
                workspace(settings, owner),
                invitation(invitation),
                invitation.getInvitedEmail(),
                invitation.getMessage());
    }

    @Transactional
    public AcceptTeamInvitationResponse acceptInvitation(String token, AcceptTeamInvitationRequest request) {
        TeamInvitation invitation = invitationFromToken(token);
        String status = determineInvitationStatus(invitation);
        if ("revoked".equals(status)) {
            throw new InvalidInputException("This invitation was revoked");
        }
        if ("expired".equals(status)) {
            invitation.setStatus("expired");
            teamInvitationRepo.save(invitation);
            throw new InvalidInputException("This invitation has expired");
        }

        String requestEmail = request == null ? null : normalizeEmail(request.email());
        if (requestEmail != null && !requestEmail.equalsIgnoreCase(invitation.getInvitedEmail())) {
            throw new InvalidInputException("This invitation was sent to a different email address");
        }

        User owner = userRepo.findByEmail(invitation.getWorkspaceEmail())
                .orElseThrow(() -> new UserNotFoundException("Workspace owner not found"));
        AppSettings settings = appSettingsRepo.findByEmail(owner.getEmail())
                .orElseGet(() -> appSettingsRepo.save(defaultSettings(owner)));
        String memberName = invitationMemberName(invitation.getInvitedEmail(), request);
        AppSettingsTeamMember member = upsertTeamMember(
                settings,
                invitation.getInvitedEmail(),
                memberName,
                invitation.getRole(),
                "active");

        invitation.setStatus("accepted");
        if (invitation.getAcceptedAt() == null) {
            invitation.setAcceptedAt(LocalDateTime.now());
        }
        teamInvitationRepo.save(invitation);

        return new AcceptTeamInvitationResponse(
                workspace(settings, owner),
                member(member),
                invitation(invitation),
                "Invitation accepted");
    }

    private TeamAccessPageResponse.Workspace workspace(AppSettings settings, User user) {
        return new TeamAccessPageResponse.Workspace(
                fallback(settings.getBusinessId(), String.valueOf(user.getUserId())),
                fallback(settings.getBusinessName(), fallback(user.getName(), "Workspace")));
    }

    private List<TeamAccessPageResponse.Member> members(
            AppSettings settings,
            User user,
            List<TeamInvitation> invitations) {
        Map<String, TeamAccessPageResponse.Member> members = new LinkedHashMap<>();
        Map<String, TeamInvitation> latestInvitationByEmail = latestInvitationByEmail(invitations);
        members.put(user.getEmail().toLowerCase(Locale.US), new TeamAccessPageResponse.Member(
                String.valueOf(user.getUserId()),
                fallback(user.getName(), user.getEmail()),
                user.getEmail(),
                determineMemberRole(user.getEmail(), null),
                determineMemberStatus(user.getEmail(), null, latestInvitationByEmail.get(user.getEmail().toLowerCase(Locale.US))),
                null));

        List<AppSettingsTeamMember> storedTeamMembers = settings.getTeamMembers();
        if (storedTeamMembers != null) {
            for (AppSettingsTeamMember member : storedTeamMembers) {
                String memberEmail = clean(member.getEmail());
                if (memberEmail == null) {
                    continue;
                }
                if (memberEmail.equalsIgnoreCase(user.getEmail())) {
                    continue;
                }
                members.put(memberEmail.toLowerCase(Locale.US), new TeamAccessPageResponse.Member(
                        fallback(clean(member.getMemberId()), memberEmail),
                        fallback(clean(member.getName()), memberEmail),
                        memberEmail,
                        determineMemberRole(memberEmail, member),
                        determineMemberStatus(memberEmail, member, latestInvitationByEmail.get(memberEmail.toLowerCase(Locale.US))),
                        null));
            }
        }

        return new ArrayList<>(members.values());
    }

    private List<TeamAccessPageResponse.Invitation> invitations(String email) {
        return invitations(teamInvitationRepo.findAllByWorkspaceEmailOrderByInvitedAtDesc(email));
    }

    private List<TeamAccessPageResponse.Invitation> invitations(List<TeamInvitation> invitations) {
        return uniqueLatestInvitations(invitations).stream()
                .map(this::invitation)
                .toList();
    }

    private TeamAccessPageResponse.Invitation invitation(TeamInvitation invitation) {
        return new TeamAccessPageResponse.Invitation(
                invitation.getId() == null ? null : String.valueOf(invitation.getId()),
                invitation.getInvitedEmail(),
                determineInvitationRole(invitation),
                determineInvitationStatus(invitation),
                invitation.getInvitedAt(),
                invitation.getExpiresAt());
    }

    private TeamAccessPageResponse.Member member(AppSettingsTeamMember member) {
        return new TeamAccessPageResponse.Member(
                fallback(clean(member.getMemberId()), clean(member.getEmail())),
                fallback(clean(member.getName()), clean(member.getEmail())),
                clean(member.getEmail()),
                determineMemberRole(member.getEmail(), member),
                normalizeMemberStatusValue(member.getStatus()),
                null);
    }

    private AppSettings defaultSettings(User user) {
        AppSettings settings = new AppSettings();
        settings.setEmail(user.getEmail());
        settings.setProfileName(fallback(user.getName(), "User"));
        settings.setBusinessId(String.valueOf(user.getUserId()));
        settings.setBusinessName(fallback(user.getName(), "Workspace"));
        settings.setBaseCurrency("CAD");
        settings.setMfaEnabled(false);
        settings.setActiveSessions(1);
        settings.setBillingPlanName("Free");
        settings.setBillingCurrency("CAD");
        settings.setBillingInterval("monthly");
        settings.setTeamMembers(List.of(new AppSettingsTeamMember(
                String.valueOf(user.getUserId()),
                fallback(user.getName(), "User"),
                user.getEmail(),
                "owner",
                "active")));
        return settings;
    }

    private String normalizeRole(String role) {
        String cleaned = clean(role);
        if (cleaned == null) {
            return DEFAULT_ROLE;
        }
        String normalized = cleaned.toLowerCase(Locale.US);
        return ALLOWED_ROLES.contains(normalized) ? normalized : DEFAULT_ROLE;
    }

    private AppSettingsTeamMember upsertTeamMember(
            AppSettings settings,
            String email,
            String name,
            String role,
            String status) {
        String memberEmail = normalizeEmail(email);
        if (memberEmail == null) {
            throw new InvalidInputException("Team member email is required");
        }

        List<AppSettingsTeamMember> members = settings.getTeamMembers() == null
                ? new ArrayList<>()
                : new ArrayList<>(settings.getTeamMembers());
        AppSettingsTeamMember existing = null;
        for (AppSettingsTeamMember candidate : members) {
            String candidateEmail = clean(candidate.getEmail());
            if (candidateEmail != null && candidateEmail.equalsIgnoreCase(memberEmail)) {
                existing = candidate;
                break;
            }
        }

        if (existing == null) {
            existing = new AppSettingsTeamMember(
                    memberIdForEmail(memberEmail),
                    fallback(clean(name), memberEmail),
                    memberEmail,
                    normalizeRole(role),
                    normalizeMemberStatusValue(status));
            members.add(existing);
        } else {
            existing.setMemberId(fallback(clean(existing.getMemberId()), memberIdForEmail(memberEmail)));
            existing.setName(fallback(clean(name), fallback(clean(existing.getName()), memberEmail)));
            existing.setEmail(memberEmail);
            existing.setRole(normalizeRole(role));
            existing.setStatus(normalizeMemberStatusValue(status));
        }

        settings.setTeamMembers(members);
        appSettingsRepo.save(settings);
        return existing;
    }

    private String memberIdForEmail(String email) {
        return userRepo.findByEmail(email)
                .map(user -> String.valueOf(user.getUserId()))
                .orElse(email.toLowerCase(Locale.US));
    }

    private String invitationMemberName(String email, AcceptTeamInvitationRequest request) {
        String requestName = request == null ? null : clean(request.name());
        if (requestName != null) {
            return requestName;
        }
        return userRepo.findByEmail(email)
                .map(User::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(email);
    }

    private String determineMemberRole(String memberEmail, AppSettingsTeamMember member) {
        if (member == null) {
            return "owner";
        }
        if ("owner".equalsIgnoreCase(clean(member.getRole()))) {
            return "owner";
        }
        return normalizeRole(member.getRole());
    }

    private String determineMemberStatus(
            String memberEmail,
            AppSettingsTeamMember member,
            TeamInvitation latestInvitation) {
        if (member == null) {
            return "active";
        }

        String explicitStatus = normalizeMemberStatusValue(member.getStatus());
        if ("owner".equals(determineMemberRole(memberEmail, member))) {
            return "active";
        }
        if ("disabled".equals(explicitStatus)) {
            return "disabled";
        }

        String invitationStatus = latestInvitation == null ? null : determineInvitationStatus(latestInvitation);
        if ("pending".equals(invitationStatus)) {
            return "invited";
        }
        if ("revoked".equals(invitationStatus)) {
            return "disabled";
        }
        if ("expired".equals(invitationStatus)) {
            return "disabled";
        }
        if ("invited".equals(explicitStatus)) {
            return "invited";
        }

        if (ACTIVE_MEMBER_STATUSES.contains(explicitStatus)) {
            return "active";
        }

        return DEFAULT_STATUS;
    }

    private String determineInvitationRole(TeamInvitation invitation) {
        return normalizeRole(invitation == null ? null : invitation.getRole());
    }

    private String determineInvitationStatus(TeamInvitation invitation) {
        if (invitation == null) {
            return "pending";
        }
        String cleaned = clean(invitation.getStatus());
        if (cleaned == null) {
            return "pending";
        }
        String normalized = cleaned.toLowerCase(Locale.US);
        if ("accepted".equals(normalized)) {
            return "accepted";
        }
        if ("revoked".equals(normalized)) {
            return "revoked";
        }
        if ("expired".equals(normalized)) {
            return "expired";
        }
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            return "expired";
        }
        return "pending";
    }

    private String normalizeMemberStatusValue(String status) {
        String cleaned = clean(status);
        if (cleaned == null) {
            return DEFAULT_STATUS;
        }
        String normalized = cleaned.toLowerCase(Locale.US);
        if (DISABLED_MEMBER_STATUSES.contains(normalized)) {
            return "disabled";
        }
        if ("invited".equals(normalized)) {
            return "invited";
        }
        if (ACTIVE_MEMBER_STATUSES.contains(normalized)) {
            return "active";
        }
        return DEFAULT_STATUS;
    }

    private Map<String, TeamInvitation> latestInvitationByEmail(List<TeamInvitation> invitations) {
        Map<String, TeamInvitation> latestByEmail = new HashMap<>();
        for (TeamInvitation invitation : uniqueLatestInvitations(invitations)) {
            String invitedEmail = clean(invitation.getInvitedEmail());
            if (invitedEmail != null) {
                latestByEmail.put(invitedEmail.toLowerCase(Locale.US), invitation);
            }
        }
        return latestByEmail;
    }

    private List<TeamInvitation> uniqueLatestInvitations(List<TeamInvitation> invitations) {
        Map<String, TeamInvitation> latestByEmail = new LinkedHashMap<>();
        invitations.stream()
                .sorted(Comparator.comparing(TeamInvitation::getInvitedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(invitation -> {
                    String invitedEmail = clean(invitation.getInvitedEmail());
                    if (invitedEmail == null) {
                        return;
                    }
                    String key = invitedEmail.toLowerCase(Locale.US);
                    TeamInvitation current = latestByEmail.get(key);
                    if (current == null || isNewerInvitation(invitation, current)) {
                        latestByEmail.put(key, invitation);
                    }
                });
        return new ArrayList<>(latestByEmail.values());
    }

    private boolean isNewerInvitation(TeamInvitation candidate, TeamInvitation existing) {
        LocalDateTime candidateInvitedAt = candidate.getInvitedAt();
        LocalDateTime existingInvitedAt = existing.getInvitedAt();
        if (candidateInvitedAt == null) {
            return false;
        }
        if (existingInvitedAt == null) {
            return true;
        }
        if (candidateInvitedAt.isAfter(existingInvitedAt)) {
            return true;
        }
        if (candidateInvitedAt.isEqual(existingInvitedAt)) {
            String candidateStatus = determineInvitationStatus(candidate);
            String existingStatus = determineInvitationStatus(existing);
            if (CLOSED_INVITATION_STATUSES.contains(candidateStatus) && !CLOSED_INVITATION_STATUSES.contains(existingStatus)) {
                return true;
            }
        }
        return false;
    }

    private TeamInvitation invitationFromToken(String token) {
        String cleanedToken = clean(token);
        if (cleanedToken == null) {
            throw new InvalidInputException("Invitation token is required");
        }

        TeamInvitation invitation = teamInvitationRepo.findByInviteTokenHash(hashToken(cleanedToken))
                .orElseThrow(() -> new InvalidInputException("Invalid invitation token"));
        String status = determineInvitationStatus(invitation);
        if ("expired".equals(status) && !"expired".equalsIgnoreCase(clean(invitation.getStatus()))) {
            invitation.setStatus("expired");
            return teamInvitationRepo.save(invitation);
        }
        return invitation;
    }

    private TokenPair newTokenPair() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new TokenPair(raw, hashToken(raw));
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String clean(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private String normalizeEmail(String email) {
        String cleaned = clean(email);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.US);
    }

    private String fallback(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private record TokenPair(String raw, String hash) {
    }
}
