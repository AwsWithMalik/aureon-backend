package com.Accounting.app.settings.dto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileSettings {
    private String name;
    private String displayName;
    private String email;
    private String avatarUrl;
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
}
