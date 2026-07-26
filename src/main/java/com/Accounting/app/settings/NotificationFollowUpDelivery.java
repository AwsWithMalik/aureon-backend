package com.Accounting.app.settings;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "notification_follow_up_delivery",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_follow_up_delivery_period",
                columnNames = {"settings_email", "notification_type", "period_key"}))
public class NotificationFollowUpDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settings_email", nullable = false)
    private String settingsEmail;

    @Column(name = "notification_type", nullable = false)
    private String notificationType;

    @Column(name = "period_key", nullable = false)
    private String periodKey;

    private LocalDateTime scheduledFor;

    private LocalDateTime deliveredAt;

    private String deliveryStatus;

    @Column(length = 1000)
    private String errorMessage;

    public Long getId() {
        return id;
    }

    public String getSettingsEmail() {
        return settingsEmail;
    }

    public void setSettingsEmail(String settingsEmail) {
        this.settingsEmail = settingsEmail;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public void setPeriodKey(String periodKey) {
        this.periodKey = periodKey;
    }

    public LocalDateTime getScheduledFor() {
        return scheduledFor;
    }

    public void setScheduledFor(LocalDateTime scheduledFor) {
        this.scheduledFor = scheduledFor;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
