package com.Accounting.app.settings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationFollowUpDeliveryRepo extends JpaRepository<NotificationFollowUpDelivery, Long> {
    boolean existsBySettingsEmailAndNotificationTypeAndPeriodKey(
            String settingsEmail,
            String notificationType,
            String periodKey);
}
