package com.Accounting.app.settings;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "app_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppSettings {
    @Id
    @Column(name = "email", nullable = false)
    private String email;

    private String profileName;
    private String displayName;
    private String avatarUrl;
    private String avatarStorageLocation;
    private String phone;
    private LocalDate dateOfBirth;
    private String addressLine1;
    private String city;
    private String region;
    private String country;
    private String language;
    private String timezone;
    private String dateFormat;
    private String communicationPreference;
    private LocalDate memberSince;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private LocalDateTime lastLoginAt;
    private String businessId;
    private String businessName;
    private String baseCurrency;
    private Boolean mfaEnabled;
    private LocalDateTime lastPasswordChangeAt;
    private Integer activeSessions;
    private Boolean quietHoursEnabled;
    private String quietHoursStart;
    private String quietHoursEnd;
    private String digestFrequency;
    private String digestDay;
    private String digestTime;
    private String billingPlanName;
    private BigDecimal billingAmount;
    private String billingCurrency;
    private String billingInterval;
    private String billingCycle;

    @OneToOne(mappedBy = "settings", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AppSettingsSecurity securityProfile;

    @ElementCollection
    @CollectionTable(
            name = "app_settings_team_members",
            joinColumns = @JoinColumn(name = "settings_email", referencedColumnName = "email"))
    private List<AppSettingsTeamMember> teamMembers = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "app_settings_notifications",
            joinColumns = @JoinColumn(name = "settings_email", referencedColumnName = "email"))
    private List<AppSettingsNotification> notifications = new ArrayList<>();
}
