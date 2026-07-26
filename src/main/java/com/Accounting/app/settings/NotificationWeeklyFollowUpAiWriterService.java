package com.Accounting.app.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.Accounting.app.auth.User;
import com.Accounting.app.auth.UserRepo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class NotificationWeeklyFollowUpAiWriterService {
    private static final Logger log = LoggerFactory.getLogger(NotificationWeeklyFollowUpAiWriterService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepo userRepo;
    private final String aiServiceBaseUrl;

    public NotificationWeeklyFollowUpAiWriterService(
            UserRepo userRepo,
            @Value("${app.ai-service.base-url:http://ai-layer:8000}") String aiServiceBaseUrl) {
        this.userRepo = userRepo;
        this.aiServiceBaseUrl = normalizeBaseUrl(aiServiceBaseUrl);
    }

    public WeeklyFollowUpDraft writeDraft(WeeklyFollowUpContent content, List<String> enabledTopics) {
        User user = userRepo.findByEmail(content.recipientEmail()).orElse(null);
        if (user == null) {
            return fallbackDraft(content, "User record was not found.");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<String> response = restTemplate.postForEntity(
                    aiUrl("/notifications/weekly-follow-up/write"),
                    new HttpEntity<>(requestBody(user, content, enabledTopics), headers),
                    String.class);

            WeeklyFollowUpDraft draft = parseDraft(response.getBody());
            if (draft.plainTextBody() == null || draft.plainTextBody().isBlank()) {
                return fallbackDraft(content, "AI writer returned an empty body.");
            }
            return draft;
        } catch (RestClientException ex) {
            log.warn("Weekly follow-up AI writer failed for {}: {}", content.recipientEmail(), ex.getMessage());
            return fallbackDraft(content, ex.getMessage());
        } catch (Exception ex) {
            log.warn("Weekly follow-up AI writer returned invalid data for {}: {}", content.recipientEmail(), ex.getMessage());
            return fallbackDraft(content, ex.getMessage());
        }
    }

    private Map<String, Object> requestBody(User user, WeeklyFollowUpContent content, List<String> enabledTopics) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getUserId());
        body.put("recipientEmail", content.recipientEmail());
        body.put("recipientName", content.recipientName());
        body.put("businessName", content.businessName());
        body.put("periodLabel", content.periodLabel());
        body.put("enabledTopics", enabledTopics == null ? List.of() : enabledTopics);
        body.put("sections", content.sections().stream()
                .map(section -> Map.of(
                        "title", section.title(),
                        "lines", section.lines() == null ? List.of() : section.lines()))
                .toList());
        return body;
    }

    private WeeklyFollowUpDraft parseDraft(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        List<WeeklyFollowUpDraftSection> sections = objectMapper.convertValue(
                root.path("sections"),
                new TypeReference<List<WeeklyFollowUpDraftSection>>() {
                });
        List<String> recommendedActions = objectMapper.convertValue(
                root.path("recommendedActions"),
                new TypeReference<List<String>>() {
                });

        return new WeeklyFollowUpDraft(
                root.path("subject").asText("Your weekly Ledger Luxe follow-up"),
                root.path("preview").asText("Your weekly finance follow-up is ready."),
                root.path("headline").asText("Weekly follow-up"),
                root.path("summary").asText("Here are your weekly finance updates."),
                sections == null ? List.of() : sections,
                recommendedActions == null ? List.of() : recommendedActions,
                root.path("plainTextBody").asText(null),
                true,
                null);
    }

    private WeeklyFollowUpDraft fallbackDraft(WeeklyFollowUpContent content, String reason) {
        return new WeeklyFollowUpDraft(
                "Your weekly Ledger Luxe follow-up",
                "Your weekly finance follow-up is ready.",
                "Weekly follow-up",
                "Here are your weekly finance updates.",
                content.sections().stream()
                        .map(section -> new WeeklyFollowUpDraftSection(section.title(), "", section.lines(), "info"))
                        .toList(),
                List.of(),
                null,
                false,
                reason);
    }

    private String aiUrl(String path) {
        if (path == null || path.isBlank()) {
            return aiServiceBaseUrl;
        }
        return path.startsWith("/") ? aiServiceBaseUrl + path : aiServiceBaseUrl + "/" + path;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank()
                ? "http://ai-layer:8000"
                : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
