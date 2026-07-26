package com.Accounting.app.settings;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Accounting.app.accounts.Account;
import com.Accounting.app.accounts.AccountRepo;
import com.Accounting.app.files.FileRepo;
import com.Accounting.app.files.UploadedFile;
import com.Accounting.app.plaid.PlaidItem;
import com.Accounting.app.plaid.PlaidItemRepo;
import com.Accounting.app.tax.TaxProfileConfig;
import com.Accounting.app.tax.TaxProfileConfigRepo;
import com.Accounting.app.tax.TaxProfileDeadlineConfig;
import com.Accounting.app.tax.TaxProfileEstimatedPaymentConfig;
import com.Accounting.app.transactions.TransactionsRepo;

@Service
public class NotificationWeeklyFollowUpService {
    private static final Logger log = LoggerFactory.getLogger(NotificationWeeklyFollowUpService.class);
    private static final String NOTIFICATION_TYPE = "weekly-follow-up";
    private static final Set<String> CLOSED_FILE_STATUSES = Set.of("extracted", "completed", "matched", "failed");
    private static final Set<String> CLOSED_TAX_STATUSES = Set.of("complete", "completed", "paid", "done", "filed", "cancelled");
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final Pattern UTC_OFFSET_PATTERN = Pattern.compile("\\(UTC([+-]\\d{2}:\\d{2})\\)");

    private final AppSettingsRepo appSettingsRepo;
    private final NotificationFollowUpDeliveryRepo deliveryRepo;
    private final NotificationWeeklyFollowUpEmailService emailService;
    private final AccountRepo accountRepo;
    private final PlaidItemRepo plaidItemRepo;
    private final FileRepo fileRepo;
    private final TaxProfileConfigRepo taxProfileConfigRepo;
    private final TransactionsRepo transactionsRepo;
    private final NotificationWeeklyFollowUpAiWriterService aiWriterService;

    public NotificationWeeklyFollowUpService(
            AppSettingsRepo appSettingsRepo,
            NotificationFollowUpDeliveryRepo deliveryRepo,
            NotificationWeeklyFollowUpEmailService emailService,
            AccountRepo accountRepo,
            PlaidItemRepo plaidItemRepo,
            FileRepo fileRepo,
            TaxProfileConfigRepo taxProfileConfigRepo,
            TransactionsRepo transactionsRepo,
            NotificationWeeklyFollowUpAiWriterService aiWriterService) {
        this.appSettingsRepo = appSettingsRepo;
        this.deliveryRepo = deliveryRepo;
        this.emailService = emailService;
        this.accountRepo = accountRepo;
        this.plaidItemRepo = plaidItemRepo;
        this.fileRepo = fileRepo;
        this.taxProfileConfigRepo = taxProfileConfigRepo;
        this.transactionsRepo = transactionsRepo;
        this.aiWriterService = aiWriterService;
    }

    @Transactional
    public void dispatchDueWeeklyFollowUps() {
        if (!emailService.isReady()) {
            return;
        }

        for (AppSettings settings : appSettingsRepo.findAll()) {
            try {
                dispatchIfDue(settings);
            } catch (RuntimeException ex) {
                log.warn("Weekly follow-up dispatch failed for {}: {}", settings.getEmail(), ex.getMessage());
            }
        }
    }

    private void dispatchIfDue(AppSettings settings) {
        if (!isWeeklyEmailFollowUpEnabled(settings)) {
            return;
        }

        ZoneId zone = resolveZone(settings.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDateTime scheduledFor = scheduledForCurrentWeek(settings, now);
        if (now.toLocalDateTime().isBefore(scheduledFor)) {
            return;
        }
        if (isInsideQuietHours(settings, now.toLocalTime())) {
            return;
        }

        String periodKey = periodKey(scheduledFor);
        if (deliveryRepo.existsBySettingsEmailAndNotificationTypeAndPeriodKey(settings.getEmail(), NOTIFICATION_TYPE, periodKey)) {
            return;
        }

        List<String> enabledTopics = enabledFollowUpTopics(settings);
        WeeklyFollowUpContent content = buildContent(settings, now);
        if (content.sections().isEmpty()) {
            return;
        }
        WeeklyFollowUpDraft draft = aiWriterService.writeDraft(content, enabledTopics);

        NotificationFollowUpDelivery delivery = new NotificationFollowUpDelivery();
        delivery.setSettingsEmail(settings.getEmail());
        delivery.setNotificationType(NOTIFICATION_TYPE);
        delivery.setPeriodKey(periodKey);
        delivery.setScheduledFor(scheduledFor);
        delivery.setDeliveryStatus("processing");
        delivery = deliveryRepo.save(delivery);

        NotificationDeliveryResult result = emailService.sendWeeklyFollowUp(content, draft);
        delivery.setDeliveryStatus(result.status());
        delivery.setErrorMessage(truncate(deliveryMessage(result, draft), 1000));
        if ("sent".equals(result.status())) {
            delivery.setDeliveredAt(LocalDateTime.now(zone));
        }
        deliveryRepo.save(delivery);
    }

    private WeeklyFollowUpContent buildContent(AppSettings settings, ZonedDateTime now) {
        List<WeeklyFollowUpSection> sections = new ArrayList<>();
        String email = settings.getEmail();
        LocalDate today = now.toLocalDate();

        if (notificationEnabled(settings, "banking-sync")) {
            sections.add(bankingSyncSection(email));
        }
        if (notificationEnabled(settings, "receipt-review")) {
            sections.add(receiptReviewSection(email, now.toLocalDateTime()));
        }
        if (notificationEnabled(settings, "tax-reminders")) {
            sections.add(taxReminderSection(email, today));
        }
        if (notificationEnabled(settings, "security-alerts")) {
            sections.add(securitySection(settings));
        }
        if (notificationEnabled(settings, "ai-insights") && notificationEnabled(settings, "channel-ai")) {
            sections.add(aiInsightsSection(email, now.toLocalDateTime()));
        }
        if (notificationEnabled(settings, "product-updates")) {
            sections.add(productUpdatesSection());
        }

        return new WeeklyFollowUpContent(
                email,
                fallback(settings.getProfileName(), settings.getDisplayName()),
                settings.getBusinessName(),
                "Week of " + now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).format(PERIOD_FORMATTER),
                sections);
    }

    private List<String> enabledFollowUpTopics(AppSettings settings) {
        List<String> topics = new ArrayList<>();
        if (notificationEnabled(settings, "banking-sync")) {
            topics.add("banking-sync");
        }
        if (notificationEnabled(settings, "receipt-review")) {
            topics.add("receipt-review");
        }
        if (notificationEnabled(settings, "tax-reminders")) {
            topics.add("tax-reminders");
        }
        if (notificationEnabled(settings, "security-alerts")) {
            topics.add("security-alerts");
        }
        if (notificationEnabled(settings, "ai-insights") && notificationEnabled(settings, "channel-ai")) {
            topics.add("ai-insights");
        }
        if (notificationEnabled(settings, "product-updates")) {
            topics.add("product-updates");
        }
        return topics;
    }

    private WeeklyFollowUpSection bankingSyncSection(String email) {
        List<PlaidItem> items = plaidItemRepo.findAllByUser_Email(email);
        List<Account> accounts = accountRepo.findAllByEmail(email);
        long needsAttention = items.stream().filter(this::syncNeedsAttention).count()
                + accounts.stream().filter(this::syncNeedsAttention).count();
        List<String> lines = new ArrayList<>();
        lines.add(items.size() + " institution(s) and " + accounts.size() + " account(s) are connected.");
        if (needsAttention > 0) {
            lines.add(needsAttention + " connection(s) need sync attention.");
        } else {
            lines.add("No banking connection issues were detected.");
        }
        return new WeeklyFollowUpSection("Banking sync", lines);
    }

    private WeeklyFollowUpSection receiptReviewSection(String email, LocalDateTime now) {
        List<UploadedFile> files = fileRepo.findAllByUser_Email(email);
        long uploadedThisWeek = files.stream()
                .filter(file -> file.getUploadedAt() != null && !file.getUploadedAt().isBefore(now.minusDays(7)))
                .count();
        long pendingReview = files.stream().filter(this::fileNeedsFollowUp).count();
        long unmatched = files.stream().filter(file -> file.getRelatedTransaction() == null).count();

        List<String> lines = new ArrayList<>();
        lines.add(uploadedThisWeek + " receipt/file upload(s) were added in the last 7 days.");
        lines.add(pendingReview + " upload(s) still need extraction or review.");
        if (unmatched > 0) {
            lines.add(unmatched + " upload(s) are not linked to a transaction yet.");
        }
        return new WeeklyFollowUpSection("Receipt review", lines);
    }

    private WeeklyFollowUpSection taxReminderSection(String email, LocalDate today) {
        List<String> lines = new ArrayList<>();
        TaxProfileConfig config = taxProfileConfigRepo.findByEmail(email).orElse(null);
        if (config == null) {
            lines.add("Your tax profile has not been configured yet.");
            return new WeeklyFollowUpSection("Tax reminders", lines);
        }

        LocalDate windowEnd = today.plusDays(14);
        List<TaxProfileDeadlineConfig> deadlines = config.getDeadlines() == null ? List.of() : config.getDeadlines();
        for (TaxProfileDeadlineConfig deadline : deadlines) {
            if (deadline.getDueDate() != null
                    && !deadline.getDueDate().isBefore(today)
                    && !deadline.getDueDate().isAfter(windowEnd)
                    && !isClosedTaxStatus(deadline.getStatus())) {
                lines.add("Deadline: " + fallback(deadline.getLabel(), "Tax deadline") + " due " + deadline.getDueDate() + ".");
            }
        }

        List<TaxProfileEstimatedPaymentConfig> payments = config.getEstimatedPayments() == null ? List.of() : config.getEstimatedPayments();
        for (TaxProfileEstimatedPaymentConfig payment : payments) {
            if (payment.getDueDate() != null
                    && !payment.getDueDate().isBefore(today)
                    && !payment.getDueDate().isAfter(windowEnd)
                    && !isClosedTaxStatus(payment.getStatus())) {
                lines.add("Estimated payment due " + payment.getDueDate() + ".");
            }
        }

        if (lines.isEmpty()) {
            lines.add("No tax deadlines or estimated payments are due in the next 14 days.");
        }
        return new WeeklyFollowUpSection("Tax reminders", lines);
    }

    private WeeklyFollowUpSection securitySection(AppSettings settings) {
        List<String> lines = new ArrayList<>();
        lines.add(Boolean.TRUE.equals(settings.getMfaEnabled()) ? "MFA is enabled." : "MFA is not enabled yet.");
        lines.add(defaultNumber(settings.getActiveSessions(), 0) + " active session(s) are recorded.");
        if (settings.getLastPasswordChangeAt() != null) {
            lines.add("Last password change: " + settings.getLastPasswordChangeAt().toLocalDate() + ".");
        }
        return new WeeklyFollowUpSection("Security", lines);
    }

    private WeeklyFollowUpSection aiInsightsSection(String email, LocalDateTime now) {
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        long recentTransactions = transactionsRepo.countByAccountEmailAndTimestampBetween(email, sevenDaysAgo, now);
        long needsReview = transactionsRepo.countNeedsReviewByAccountEmail(email);
        List<String> lines = new ArrayList<>();
        lines.add(recentTransactions + " transaction(s) posted in the last 7 days.");
        lines.add(needsReview + " transaction(s) are marked for review.");
        if (needsReview > 0) {
            lines.add("Review uncategorized or flagged transactions before your next report cycle.");
        }
        return new WeeklyFollowUpSection("AI insights", lines);
    }

    private WeeklyFollowUpSection productUpdatesSection() {
        return new WeeklyFollowUpSection(
                "Product updates",
                List.of("No product-update campaign is configured yet."));
    }

    private boolean isWeeklyEmailFollowUpEnabled(AppSettings settings) {
        if (settings.getEmail() == null || settings.getEmail().isBlank()) {
            return false;
        }
        if (!Boolean.TRUE.equals(settings.getEmailVerified())) {
            return false;
        }
        if (!notificationEnabled(settings, "channel-email")) {
            return false;
        }
        String frequency = clean(settings.getDigestFrequency());
        return frequency == null || frequency.toLowerCase(Locale.US).contains("week");
    }

    private boolean notificationEnabled(AppSettings settings, String notificationId) {
        List<AppSettingsNotification> notifications = settings.getNotifications();
        if (notifications == null || notifications.isEmpty()) {
            return defaultNotificationEnabled(notificationId);
        }

        Map<String, Boolean> enabledById = notifications.stream()
                .filter(notification -> clean(notification.getNotificationId()) != null)
                .collect(Collectors.toMap(
                        notification -> clean(notification.getNotificationId()).toLowerCase(Locale.US),
                        notification -> Boolean.TRUE.equals(notification.getEnabled()),
                        (left, right) -> right));
        return enabledById.getOrDefault(notificationId.toLowerCase(Locale.US), defaultNotificationEnabled(notificationId));
    }

    private boolean defaultNotificationEnabled(String notificationId) {
        return !"channel-mobile".equals(notificationId) && !"product-updates".equals(notificationId);
    }

    private LocalDateTime scheduledForCurrentWeek(AppSettings settings, ZonedDateTime now) {
        DayOfWeek day = dayOfWeek(settings.getDigestDay());
        LocalTime time = parseTime(settings.getDigestTime(), LocalTime.of(8, 0));
        LocalDate monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate scheduledDate = monday.plusDays(day.getValue() - 1L);
        return LocalDateTime.of(scheduledDate, time);
    }

    private boolean isInsideQuietHours(AppSettings settings, LocalTime now) {
        if (!Boolean.TRUE.equals(settings.getQuietHoursEnabled())) {
            return false;
        }
        LocalTime start = parseTime(settings.getQuietHoursStart(), LocalTime.of(18, 0));
        LocalTime end = parseTime(settings.getQuietHoursEnd(), LocalTime.of(8, 0));
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private DayOfWeek dayOfWeek(String day) {
        String normalized = clean(day);
        if (normalized == null) {
            return DayOfWeek.MONDAY;
        }
        String prefix = normalized.toLowerCase(Locale.US);
        if (prefix.startsWith("tue")) {
            return DayOfWeek.TUESDAY;
        }
        if (prefix.startsWith("wed")) {
            return DayOfWeek.WEDNESDAY;
        }
        if (prefix.startsWith("thu")) {
            return DayOfWeek.THURSDAY;
        }
        if (prefix.startsWith("fri")) {
            return DayOfWeek.FRIDAY;
        }
        if (prefix.startsWith("sat")) {
            return DayOfWeek.SATURDAY;
        }
        if (prefix.startsWith("sun")) {
            return DayOfWeek.SUNDAY;
        }
        return DayOfWeek.MONDAY;
    }

    private LocalTime parseTime(String value, LocalTime fallback) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return fallback;
        }
        try {
            return LocalTime.parse(cleaned);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private ZoneId resolveZone(String timezone) {
        String cleaned = clean(timezone);
        if (cleaned == null) {
            return ZoneId.systemDefault();
        }
        if (cleaned.toLowerCase(Locale.US).contains("toronto")
                || cleaned.toLowerCase(Locale.US).contains("eastern")) {
            return ZoneId.of("America/Toronto");
        }
        try {
            return ZoneId.of(cleaned);
        } catch (RuntimeException ignored) {
            Matcher matcher = UTC_OFFSET_PATTERN.matcher(cleaned);
            if (matcher.find()) {
                return ZoneOffset.of(matcher.group(1));
            }
            return ZoneId.systemDefault();
        }
    }

    private String periodKey(LocalDateTime scheduledFor) {
        int weekYear = scheduledFor.get(IsoFields.WEEK_BASED_YEAR);
        int week = scheduledFor.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return weekYear + "-W" + String.format(Locale.US, "%02d", week);
    }

    private boolean syncNeedsAttention(PlaidItem item) {
        return item.getLastAccountSyncFailureAt() != null
                && (item.getLastAccountSyncSuccessAt() == null
                || item.getLastAccountSyncFailureAt().isAfter(item.getLastAccountSyncSuccessAt()));
    }

    private boolean syncNeedsAttention(Account account) {
        return account.getLastSyncFailureAt() != null
                && (account.getLastSyncSuccessAt() == null
                || account.getLastSyncFailureAt().isAfter(account.getLastSyncSuccessAt()));
    }

    private boolean fileNeedsFollowUp(UploadedFile file) {
        String status = clean(file.getStatus());
        return status == null || !CLOSED_FILE_STATUSES.contains(status.toLowerCase(Locale.US));
    }

    private boolean isClosedTaxStatus(String status) {
        String cleaned = clean(status);
        return cleaned != null && CLOSED_TAX_STATUSES.contains(cleaned.toLowerCase(Locale.US));
    }

    private Integer defaultNumber(Integer value, Integer fallback) {
        return value != null ? value : fallback;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String deliveryMessage(NotificationDeliveryResult result, WeeklyFollowUpDraft draft) {
        String aiStatus = draft != null && draft.aiGenerated()
                ? "AI draft generated"
                : "Java fallback draft used" + (draft == null || draft.fallbackReason() == null ? "" : ": " + draft.fallbackReason());
        return result.message() + " " + aiStatus;
    }

    private String clean(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private String fallback(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
