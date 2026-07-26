package com.Accounting.app.auth;

import com.Accounting.app.auth.dto.LoginResult;
import com.Accounting.app.exceptions.InvalidInputException;
import com.Accounting.app.exceptions.UserNotFoundException;
import com.Accounting.app.settings.AppSettings;
import com.Accounting.app.settings.AppSettingsRepo;
import com.Accounting.app.settings.AppSettingsSecurity;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaService {

    private final UserRepo userRepo;
    private final JwtService jwtService;
    private final AppSettingsRepo appSettingsRepo;
    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator(
            new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                    .setWindowSize(3)
                    .build());

    public MfaService(UserRepo userRepo, JwtService jwtService, AppSettingsRepo appSettingsRepo) {
        this.userRepo = userRepo;
        this.jwtService = jwtService;
        this.appSettingsRepo = appSettingsRepo;
    }

    @Transactional
    public MfaSetupResponse startSetup(Integer userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        String secret = key.getKey();

        user.setMfaSecret(secret);
        user.setMfaEnabled(false);
        userRepo.save(user);
        syncSecuritySettings(user, false);

        String issuer = "Ledger Luxe";
        String accountName = user.getEmail();

        String qrUri = String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s",
                issuer,
                accountName,
                secret,
                issuer);

        return new MfaSetupResponse(secret, qrUri);
    }

    public boolean verifyCode(Integer userId, String code) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
            return false;
        }

        return googleAuthenticator.authorize(user.getMfaSecret(), normalizedCode(code));
    }

    public LoginResult verifyMfaLogin(String mfaToken, String code) {
        if (mfaToken == null || mfaToken.isBlank()) {
            throw new InvalidInputException("MFA session expired. Please log in again.");
        }

        String email = jwtService.extractEmailFromMfaToken(mfaToken);

        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        boolean valid = verifyCode(user.getUserId(), code);

        if (!valid) {
            throw new InvalidInputException("Invalid MFA code.");
        }

        return new LoginResult(
                "AUTHENTICATED",
                user.getEmail(),
                null);
    }

    @Transactional
    public void enableMfa(Integer userId, String code) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        boolean valid = verifyCode(userId, code);

        if (!valid) {
            throw new InvalidInputException("Invalid MFA code.");
        }

        user.setMfaEnabled(true);
        user.setMfaEnabledAt(java.time.LocalDateTime.now());
        userRepo.save(user);
        syncSecuritySettings(user, true);
    }

    @Transactional
    public void disableMfa(Integer userId, String code) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        boolean valid = verifyCode(userId, code);

        if (!valid) {
            throw new InvalidInputException("Invalid MFA code.");
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaEnabledAt(null);
        userRepo.save(user);
        syncSecuritySettings(user, false);
    }

    private int normalizedCode(String code) {
        String normalized = code == null ? "" : code.replaceAll("[^0-9]", "");
        if (!normalized.matches("\\d{6}")) {
            throw new InvalidInputException("MFA code must be 6 digits.");
        }
        return Integer.parseInt(normalized);
    }

    private void syncSecuritySettings(User user, boolean mfaEnabled) {
        appSettingsRepo.findByEmail(user.getEmail()).ifPresent(settings -> {
            settings.setMfaEnabled(mfaEnabled);
            AppSettingsSecurity securityProfile = ensureSecurityProfile(settings, user);
            securityProfile.setMfaEnabled(mfaEnabled);
            appSettingsRepo.save(settings);
        });
    }

    private AppSettingsSecurity ensureSecurityProfile(AppSettings settings, User user) {
        if (settings.getSecurityProfile() == null) {
            AppSettingsSecurity securityProfile = new AppSettingsSecurity();
            securityProfile.setSettings(settings);
            securityProfile.setSettingsEmail(settings.getEmail());
            securityProfile.setRecoveryEmail(user.getEmail());
            securityProfile.setBackupCodesEnabled(true);
            securityProfile.setBackupCodesRemaining(8);
            settings.setSecurityProfile(securityProfile);
        }

        AppSettingsSecurity securityProfile = settings.getSecurityProfile();
        securityProfile.setSettings(settings);
        securityProfile.setSettingsEmail(settings.getEmail());
        return securityProfile;
    }
}
