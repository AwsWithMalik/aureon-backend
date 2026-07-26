package com.Accounting.app.settings;

import com.Accounting.app.accounts.dto.Balance;
import com.Accounting.app.auth.User;
import com.Accounting.app.auth.UserRepo;
import com.Accounting.app.exceptions.InvalidInputException;
import com.Accounting.app.exceptions.UserNotFoundException;
import com.Accounting.app.files.storage.S3DocumentStorageService;
import com.Accounting.app.files.storage.StoredS3Object;
import com.Accounting.app.files.security.UploadSecurityValidator;
import com.Accounting.app.files.security.UploadSecurityValidator.ValidatedUpload;
import com.Accounting.app.settings.dto.BillingSettings;
import com.Accounting.app.settings.dto.BusinessSettings;
import com.Accounting.app.settings.dto.NotificationPreferencesSettings;
import com.Accounting.app.settings.dto.NotificationSetting;
import com.Accounting.app.settings.dto.ProfilePhotoContent;
import com.Accounting.app.settings.dto.ProfileSettings;
import com.Accounting.app.settings.dto.SecuritySettings;
import com.Accounting.app.settings.dto.SettingsPageResponse;
import com.Accounting.app.settings.dto.TeamMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SettingsPageServices {
    private static final String DEFAULT_CURRENCY = "CAD";
    private static final String DEFAULT_LANGUAGE = "English (Canada)";
    private static final String DEFAULT_TIMEZONE = "(UTC-05:00) Eastern Time (Toronto)";
    private static final String DEFAULT_DATE_FORMAT = "YYYY-MM-DD";
    private static final String DEFAULT_COMMUNICATION_PREFERENCE = "Email - Important updates & alerts";
    private static final String DEFAULT_DIGEST_FREQUENCY = "Weekly";
    private static final String DEFAULT_DIGEST_DAY = "Monday";
    private static final String DEFAULT_DIGEST_TIME = "08:00";
    private static final String DEFAULT_QUIET_HOURS_START = "18:00";
    private static final String DEFAULT_QUIET_HOURS_END = "08:00";
    private final UserRepo userRepo;
    private final AppSettingsRepo appSettingsRepo;
    private final S3DocumentStorageService storageService;
    private final UploadSecurityValidator uploadSecurityValidator;

    public SettingsPageServices(
            UserRepo userRepo,
            AppSettingsRepo appSettingsRepo,
            S3DocumentStorageService storageService,
            UploadSecurityValidator uploadSecurityValidator) {
        this.userRepo = userRepo;
        this.appSettingsRepo = appSettingsRepo;
        this.storageService = storageService;
        this.uploadSecurityValidator = uploadSecurityValidator;
    }

    @Transactional
    public SettingsPageResponse settingsPageResponse(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        AppSettings settings = appSettingsRepo.findByEmail(email)
                .orElseGet(() -> defaultSettings(user));
        ensureSecurityProfile(settings, user);
        return toResponse(user, settings);
    }

    @Transactional
    public SettingsPageResponse updateSettings(String email, SettingsPageResponse request) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        AppSettings settings = appSettingsRepo.findByEmail(email)
                .orElseGet(() -> defaultSettings(user));
        AppSettingsSecurity securityProfile = ensureSecurityProfile(settings, user);

        if (request.getProfile() != null) {
            ProfileSettings profile = request.getProfile();
            String profileName = clean(profile.getName());
            if (profileName != null) {
                settings.setProfileName(profileName);
                user.setName(profileName);
            }
            settings.setDisplayName(fallback(clean(profile.getDisplayName()), settings.getDisplayName()));
            if (settings.getAvatarStorageLocation() == null || settings.getAvatarStorageLocation().isBlank()) {
                settings.setAvatarUrl(clean(profile.getAvatarUrl()));
            }
            settings.setPhone(clean(profile.getPhone()));
            settings.setDateOfBirth(profile.getDateOfBirth());
            settings.setAddressLine1(clean(profile.getAddressLine1()));
            settings.setCity(clean(profile.getCity()));
            settings.setRegion(clean(profile.getRegion()));
            settings.setCountry(clean(profile.getCountry()));
            settings.setLanguage(fallback(clean(profile.getLanguage()), settings.getLanguage()));
            settings.setTimezone(fallback(clean(profile.getTimezone()), settings.getTimezone()));
            settings.setDateFormat(fallback(clean(profile.getDateFormat()), settings.getDateFormat()));
            settings.setCommunicationPreference(fallback(clean(profile.getCommunicationPreference()), settings.getCommunicationPreference()));
            settings.setMemberSince(profile.getMemberSince() != null ? profile.getMemberSince() : settings.getMemberSince());
            settings.setEmailVerified(defaultBoolean(profile.getEmailVerified(), settings.getEmailVerified()));
            settings.setPhoneVerified(defaultBoolean(profile.getPhoneVerified(), settings.getPhoneVerified()));
            settings.setLastLoginAt(profile.getLastLoginAt() != null ? profile.getLastLoginAt() : settings.getLastLoginAt());
        }

        if (request.getBusiness() != null) {
            settings.setBusinessId(fallback(clean(request.getBusiness().getId()), settings.getBusinessId()));
            settings.setBusinessName(fallback(clean(request.getBusiness().getName()), settings.getBusinessName()));
            settings.setBaseCurrency(normalizeCurrency(request.getBusiness().getBaseCurrency(), settings.getBaseCurrency()));
        }

        if (request.getTeamMembers() != null) {
            settings.setTeamMembers(toStoredTeamMembers(request.getTeamMembers()));
        }

        if (request.getNotifications() != null) {
            settings.setNotifications(toStoredNotifications(request.getNotifications()));
        }

        if (request.getNotificationPreferences() != null) {
            NotificationPreferencesSettings preferences = request.getNotificationPreferences();
            settings.setQuietHoursEnabled(defaultBoolean(preferences.getQuietHoursEnabled(), settings.getQuietHoursEnabled()));
            settings.setQuietHoursStart(fallback(clean(preferences.getQuietHoursStart()), settings.getQuietHoursStart()));
            settings.setQuietHoursEnd(fallback(clean(preferences.getQuietHoursEnd()), settings.getQuietHoursEnd()));
            settings.setDigestFrequency(fallback(clean(preferences.getDigestFrequency()), settings.getDigestFrequency()));
            settings.setDigestDay(fallback(clean(preferences.getDigestDay()), settings.getDigestDay()));
            settings.setDigestTime(fallback(clean(preferences.getDigestTime()), settings.getDigestTime()));
        }

        if (request.getSecurity() != null) {
            SecuritySettings security = request.getSecurity();
            Boolean mfaEnabled = Boolean.TRUE.equals(user.getMfaEnabled());
            settings.setMfaEnabled(mfaEnabled);
            securityProfile.setMfaEnabled(mfaEnabled);
            settings.setLastPasswordChangeAt(security.getLastPasswordChangeAt());
            settings.setActiveSessions(defaultNumber(security.getActiveSessions(), settings.getActiveSessions()));
            securityProfile.setRecoveryEmail(fallback(clean(security.getRecoveryEmail()), user.getEmail()));
            securityProfile.setRecoveryPhone(clean(security.getRecoveryPhone()));
            securityProfile.setBackupCodesEnabled(defaultBoolean(security.getBackupCodesEnabled(), true));
            securityProfile.setBackupCodesRemaining(defaultNumber(security.getBackupCodesRemaining(), 8));
        }

        if (request.getBilling() != null) {
            settings.setBillingPlanName(fallback(clean(request.getBilling().getPlanName()), settings.getBillingPlanName()));
            settings.setBillingInterval(fallback(clean(request.getBilling().getInterval()), settings.getBillingInterval()));
            settings.setBillingCycle(clean(request.getBilling().getBillingCycle()));
            if (request.getBilling().getAmount() != null) {
                settings.setBillingAmount(defaultAmount(request.getBilling().getAmount().getAmount(), settings.getBillingAmount()));
                settings.setBillingCurrency(normalizeCurrency(request.getBilling().getAmount().getCurrency(), settings.getBillingCurrency()));
            }
        }

        userRepo.save(user);
        AppSettings savedSettings = appSettingsRepo.save(settings);
        return toResponse(user, savedSettings);
    }

    @Transactional
    public SettingsPageResponse updateProfilePhoto(String email, MultipartFile file) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        AppSettings settings = appSettingsRepo.findByEmail(email)
                .orElseGet(() -> defaultSettings(user));

        ValidatedUpload validatedUpload = uploadSecurityValidator.validateProfilePhoto(file);

        try {
            String previousStorageLocation = settings.getAvatarStorageLocation();
            StoredS3Object storedObject = storageService.storeProfilePhoto(
                    file,
                    user.getUserId(),
                    validatedUpload.originalFilename(),
                    validatedUpload.contentType());
            settings.setAvatarStorageLocation(storedObject.location());
            settings.setAvatarUrl("/api/dashboard/settings/profile-photo/content?v=" + System.currentTimeMillis());

            AppSettings savedSettings = appSettingsRepo.save(settings);
            storageService.deleteIfManagedLocation(previousStorageLocation);
            return toResponse(user, savedSettings);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to upload profile photo", exception);
        }
    }

    @Transactional(readOnly = true)
    public ProfilePhotoContent profilePhotoContent(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        AppSettings settings = appSettingsRepo.findByEmail(user.getEmail())
                .orElseThrow(() -> new InvalidInputException("Profile photo not found"));

        String storageLocation = settings.getAvatarStorageLocation();
        if (storageLocation == null || storageLocation.isBlank() || !storageService.isManagedLocation(storageLocation)) {
            throw new InvalidInputException("Profile photo not found");
        }

        String fileName = storageService.storedFileName(storageLocation);
        return new ProfilePhotoContent(
                storageService.createPresignedInlineUri(storageLocation, fileName),
                fileName,
                contentTypeForExtension(extensionFromFilename(fileName)));
    }

    private AppSettings defaultSettings(User user) {
        AppSettings settings = new AppSettings();
        settings.setEmail(user.getEmail());
        settings.setProfileName(fallback(user.getName(), "User"));
        settings.setDisplayName(fallback(user.getName(), "User"));
        settings.setBusinessId(user.getUserId().toString());
        settings.setBusinessName(fallback(user.getName(), "Business"));
        settings.setBaseCurrency(DEFAULT_CURRENCY);
        settings.setLanguage(DEFAULT_LANGUAGE);
        settings.setTimezone(DEFAULT_TIMEZONE);
        settings.setDateFormat(DEFAULT_DATE_FORMAT);
        settings.setCommunicationPreference(DEFAULT_COMMUNICATION_PREFERENCE);
        settings.setMemberSince(LocalDate.now());
        settings.setEmailVerified(true);
        settings.setPhoneVerified(false);
        settings.setLastLoginAt(LocalDateTime.now());
        settings.setMfaEnabled(defaultBoolean(user.getMfaEnabled(), false));
        settings.setActiveSessions(1);
        settings.setQuietHoursEnabled(true);
        settings.setQuietHoursStart(DEFAULT_QUIET_HOURS_START);
        settings.setQuietHoursEnd(DEFAULT_QUIET_HOURS_END);
        settings.setDigestFrequency(DEFAULT_DIGEST_FREQUENCY);
        settings.setDigestDay(DEFAULT_DIGEST_DAY);
        settings.setDigestTime(DEFAULT_DIGEST_TIME);
        settings.setBillingPlanName("Free");
        settings.setBillingAmount(BigDecimal.ZERO);
        settings.setBillingCurrency(DEFAULT_CURRENCY);
        settings.setBillingInterval("monthly");
        settings.setTeamMembers(List.of(new AppSettingsTeamMember(
                user.getUserId().toString(),
                fallback(user.getName(), "User"),
                user.getEmail(),
                "owner",
                "active")));
        settings.setNotifications(defaultStoredNotifications());
        settings.setSecurityProfile(defaultSecurityProfile(settings, user));
        return settings;
    }

    private AppSettingsSecurity ensureSecurityProfile(AppSettings settings, User user) {
        if (settings.getSecurityProfile() == null) {
            settings.setSecurityProfile(defaultSecurityProfile(settings, user));
        } else {
            AppSettingsSecurity profile = settings.getSecurityProfile();
            profile.setSettings(settings);
            profile.setSettingsEmail(settings.getEmail());
            if (profile.getMfaEnabled() == null) {
                profile.setMfaEnabled(defaultBoolean(user.getMfaEnabled(), defaultBoolean(settings.getMfaEnabled(), false)));
            }
            if (profile.getRecoveryEmail() == null || profile.getRecoveryEmail().isBlank()) {
                profile.setRecoveryEmail(user.getEmail());
            }
            if (profile.getBackupCodesEnabled() == null) {
                profile.setBackupCodesEnabled(true);
            }
            if (profile.getBackupCodesRemaining() == null) {
                profile.setBackupCodesRemaining(8);
            }
        }
        return settings.getSecurityProfile();
    }

    private AppSettingsSecurity defaultSecurityProfile(AppSettings settings, User user) {
        AppSettingsSecurity securityProfile = new AppSettingsSecurity();
        securityProfile.setSettings(settings);
        securityProfile.setSettingsEmail(settings.getEmail());
        securityProfile.setMfaEnabled(defaultBoolean(user.getMfaEnabled(), defaultBoolean(settings.getMfaEnabled(), false)));
        securityProfile.setRecoveryEmail(user.getEmail());
        securityProfile.setRecoveryPhone(clean(settings.getPhone()));
        securityProfile.setBackupCodesEnabled(true);
        securityProfile.setBackupCodesRemaining(8);
        return securityProfile;
    }

    private SettingsPageResponse toResponse(User user, AppSettings settings) {
        AppSettingsSecurity securityProfile = ensureSecurityProfile(settings, user);
        return new SettingsPageResponse(
                new ProfileSettings(
                        fallback(settings.getProfileName(), fallback(user.getName(), "User")),
                        fallback(settings.getDisplayName(), fallback(settings.getProfileName(), fallback(user.getName(), "User"))),
                        user.getEmail(),
                        settings.getAvatarUrl(),
                        settings.getPhone(),
                        settings.getDateOfBirth(),
                        settings.getAddressLine1(),
                        settings.getCity(),
                        settings.getRegion(),
                        settings.getCountry(),
                        fallback(settings.getLanguage(), DEFAULT_LANGUAGE),
                        fallback(settings.getTimezone(), DEFAULT_TIMEZONE),
                        fallback(settings.getDateFormat(), DEFAULT_DATE_FORMAT),
                        fallback(settings.getCommunicationPreference(), DEFAULT_COMMUNICATION_PREFERENCE),
                        settings.getMemberSince(),
                        defaultBoolean(settings.getEmailVerified(), true),
                        defaultBoolean(settings.getPhoneVerified(), false),
                        settings.getLastLoginAt()),
                new BusinessSettings(fallback(settings.getBusinessId(), user.getUserId().toString()),
                        fallback(settings.getBusinessName(), fallback(user.getName(), "Business")),
                        normalizeCurrency(settings.getBaseCurrency(), DEFAULT_CURRENCY)),
                toTeamMembers(settings, user),
                toNotifications(settings),
                new NotificationPreferencesSettings(
                        defaultBoolean(settings.getQuietHoursEnabled(), true),
                        fallback(clean(settings.getQuietHoursStart()), DEFAULT_QUIET_HOURS_START),
                        fallback(clean(settings.getQuietHoursEnd()), DEFAULT_QUIET_HOURS_END),
                        fallback(clean(settings.getDigestFrequency()), DEFAULT_DIGEST_FREQUENCY),
                        fallback(clean(settings.getDigestDay()), DEFAULT_DIGEST_DAY),
                        fallback(clean(settings.getDigestTime()), DEFAULT_DIGEST_TIME)),
                new SecuritySettings(
                        Boolean.TRUE.equals(securityProfile.getMfaEnabled()),
                        settings.getLastPasswordChangeAt(),
                        defaultNumber(settings.getActiveSessions(), 1),
                        fallback(clean(securityProfile.getRecoveryEmail()), user.getEmail()),
                        clean(securityProfile.getRecoveryPhone()),
                        defaultBoolean(securityProfile.getBackupCodesEnabled(), true),
                        defaultNumber(securityProfile.getBackupCodesRemaining(), 8)),
                new BillingSettings(
                        fallback(settings.getBillingPlanName(), "Free"),
                        new Balance(defaultAmount(settings.getBillingAmount(), BigDecimal.ZERO),
                                normalizeCurrency(settings.getBillingCurrency(), DEFAULT_CURRENCY)),
                        fallback(settings.getBillingInterval(), "monthly"),
                        settings.getBillingCycle()));
    }

    private List<TeamMember> toTeamMembers(AppSettings settings, User user) {
        List<AppSettingsTeamMember> storedTeamMembers = settings.getTeamMembers();
        if (storedTeamMembers == null || storedTeamMembers.isEmpty()) {
            return List.of(new TeamMember(user.getUserId().toString(), fallback(user.getName(), "User"), user.getEmail(),
                    "owner", "active"));
        }
        return storedTeamMembers.stream()
                .map(member -> new TeamMember(
                        member.getMemberId(),
                        member.getName(),
                        member.getEmail(),
                        member.getRole(),
                        member.getStatus()))
                .toList();
    }

    private List<NotificationSetting> toNotifications(AppSettings settings) {
        List<AppSettingsNotification> storedNotifications = settings.getNotifications();
        if (storedNotifications == null || storedNotifications.isEmpty()) {
            storedNotifications = defaultStoredNotifications();
        }
        return storedNotifications.stream()
                .map(notification -> new NotificationSetting(
                        notification.getNotificationId(),
                        notification.getLabel(),
                        Boolean.TRUE.equals(notification.getEnabled())))
                .toList();
    }

    private List<AppSettingsTeamMember> toStoredTeamMembers(List<TeamMember> teamMembers) {
        List<AppSettingsTeamMember> storedTeamMembers = new ArrayList<>();
        for (TeamMember member : teamMembers) {
            storedTeamMembers.add(new AppSettingsTeamMember(
                    clean(member.getId()),
                    clean(member.getName()),
                    clean(member.getEmail()),
                    clean(member.getRole()),
                    clean(member.getStatus())));
        }
        return storedTeamMembers;
    }

    private List<AppSettingsNotification> toStoredNotifications(List<NotificationSetting> notifications) {
        List<AppSettingsNotification> storedNotifications = new ArrayList<>();
        for (NotificationSetting notification : notifications) {
            storedNotifications.add(new AppSettingsNotification(
                    clean(notification.getId()),
                    clean(notification.getLabel()),
                    Boolean.TRUE.equals(notification.getEnabled())));
        }
        return storedNotifications;
    }

    private List<AppSettingsNotification> defaultStoredNotifications() {
        return List.of(
                new AppSettingsNotification("channel-email", "Email", true),
                new AppSettingsNotification("channel-in-app", "In-app", true),
                new AppSettingsNotification("channel-mobile", "Mobile push", false),
                new AppSettingsNotification("channel-ai", "AI assistant", true),
                new AppSettingsNotification("security-alerts", "Security alerts", true),
                new AppSettingsNotification("banking-sync", "Banking sync", true),
                new AppSettingsNotification("receipt-review", "Receipt review", true),
                new AppSettingsNotification("tax-reminders", "Tax reminders", true),
                new AppSettingsNotification("ai-insights", "AI insights", true),
                new AppSettingsNotification("product-updates", "Product updates", false));
    }

    private String extensionFromFilename(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 && dotIndex < fileName.length() - 1
                ? fileName.substring(dotIndex + 1)
                : "";
    }

    private String contentTypeForExtension(String extension) {
        return switch (extension.toLowerCase()) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private String normalizeCurrency(String currency, String fallback) {
        if (currency == null || currency.isBlank()) {
            return fallback;
        }
        String normalized = currency.trim().toUpperCase();
        return List.of("USD", "CAD").contains(normalized) ? normalized : fallback;
    }

    private Integer defaultNumber(Integer value, Integer fallback) {
        return value != null ? value : fallback;
    }

    private BigDecimal defaultAmount(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }

    private Boolean defaultBoolean(Boolean value, Boolean fallback) {
        return value != null ? value : fallback;
    }

    private String clean(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private String fallback(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}

