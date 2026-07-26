package com.Accounting.app.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "app_settings_security")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppSettingsSecurity {
    @Id
    @Column(name = "settings_email", nullable = false)
    private String settingsEmail;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "settings_email", referencedColumnName = "email", foreignKey = @ForeignKey(name = "fk_app_settings_security_settings"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AppSettings settings;

    private Boolean mfaEnabled;
    private String recoveryEmail;
    private String recoveryPhone;
    private Boolean backupCodesEnabled;
    private Integer backupCodesRemaining;
}
