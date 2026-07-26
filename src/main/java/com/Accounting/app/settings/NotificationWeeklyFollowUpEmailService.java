package com.Accounting.app.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationWeeklyFollowUpEmailService {
    private static final Logger log = LoggerFactory.getLogger(NotificationWeeklyFollowUpEmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean mailEnabled;
    private final String fromAddress;

    public NotificationWeeklyFollowUpEmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.notifications.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.notifications.mail.from:no-reply@ledger-luxe.local}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailEnabled = mailEnabled;
        this.fromAddress = fromAddress;
    }

    public boolean isReady() {
        return mailEnabled && mailSenderProvider.getIfAvailable() != null;
    }

    public NotificationDeliveryResult sendWeeklyFollowUp(WeeklyFollowUpContent content, WeeklyFollowUpDraft draft) {
        if (!mailEnabled) {
            return new NotificationDeliveryResult("disabled", "Notification email delivery is disabled.");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return new NotificationDeliveryResult("not_configured", "Notification email sender is not configured.");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(content.recipientEmail());
            message.setSubject(subject(draft));
            message.setText(emailBody(content, draft));
            mailSender.send(message);
            return new NotificationDeliveryResult("sent", "Weekly follow-up email sent.");
        } catch (MailException ex) {
            log.warn("Failed to send weekly follow-up email to {}: {}", content.recipientEmail(), ex.getMessage());
            return new NotificationDeliveryResult("failed", ex.getMessage());
        }
    }

    private String subject(WeeklyFollowUpDraft draft) {
        if (draft != null && draft.subject() != null && !draft.subject().isBlank()) {
            return draft.subject();
        }
        return "Your weekly Ledger Luxe follow-up";
    }

    private String emailBody(WeeklyFollowUpContent content, WeeklyFollowUpDraft draft) {
        if (draft != null && draft.plainTextBody() != null && !draft.plainTextBody().isBlank()) {
            return draft.plainTextBody();
        }

        StringBuilder body = new StringBuilder();
        body.append("Hi ")
                .append(valueOrFallback(content.recipientName(), "there"))
                .append(",\n\n");
        body.append("Here is your weekly follow-up for ")
                .append(valueOrFallback(content.businessName(), "your workspace"))
                .append(" (")
                .append(content.periodLabel())
                .append(").\n\n");

        for (WeeklyFollowUpSection section : content.sections()) {
            body.append(section.title()).append("\n");
            for (String line : section.lines()) {
                body.append("- ").append(line).append("\n");
            }
            body.append("\n");
        }

        body.append("You can change these follow-ups from Settings > Notifications.");
        return body.toString();
    }

    private String valueOrFallback(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
