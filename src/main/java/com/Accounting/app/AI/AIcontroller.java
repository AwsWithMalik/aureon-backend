package com.Accounting.app.AI;

import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.Accounting.app.AI.dto.AgentMessageRequest;
import com.Accounting.app.AI.dto.AgentMessagesResponse;
import com.Accounting.app.AI.dto.AgentSessionDetailResponse;
import com.Accounting.app.AI.dto.AgentSessionsResponse;
import com.Accounting.app.AI.dto.AgentFoldersResponse;
import com.Accounting.app.AI.dto.AgentFilesResponse;
import com.Accounting.app.AI.dto.AgentFolderDto;
import com.Accounting.app.AI.dto.AgentMutationResponse;
import com.Accounting.app.AI.dto.AgentPdfReportResponse;
import com.Accounting.app.AI.dto.CreateAgentFolderRequest;
import com.Accounting.app.AI.dto.UpdateAgentSessionRequest;
import com.Accounting.app.auth.Config;
import com.Accounting.app.auth.User;
import com.Accounting.app.auth.UserRepo;
import com.Accounting.app.exceptions.UserNotFoundException;
import com.Accounting.app.files.DocumentType;

@RestController
public class AIcontroller {
    private final AIservice aiService;
    private final AgentWorkspaceService agentWorkspaceService;
    private final Config config;
    private final UserRepo userRepo;

    public AIcontroller(
            AIservice aiService,
            AgentWorkspaceService agentWorkspaceService,
            Config config,
            UserRepo userRepo) {
        this.aiService = aiService;
        this.agentWorkspaceService = agentWorkspaceService;
        this.config = config;
        this.userRepo = userRepo;
    }

    @GetMapping("/api/agent/sessions")
    public AgentSessionsResponse sessions() {
        return agentWorkspaceService.sessions(config.getEmail());
    }

    @GetMapping("/api/agent/sessions/{id}")
    public AgentSessionDetailResponse session(@PathVariable String id) {
        return agentWorkspaceService.session(config.getEmail(), id);
    }

    @PostMapping("/api/agent/sessions")
    public AgentSessionDetailResponse createSession() {
        return agentWorkspaceService.createSession(config.getEmail());
    }

    @PostMapping("/api/agent/messages")
    public AgentMessagesResponse sendMessage(@RequestBody AgentMessageRequest request) {
        return agentWorkspaceService.sendMessage(config.getEmail(), request);
    }

    @PostMapping("/api/agent/messages/{messageId}/pdf")
    public AgentPdfReportResponse createMessagePdf(@PathVariable String messageId) {
        return agentWorkspaceService.createPdfReport(config.getEmail(), messageId);
    }

    @GetMapping("/api/agent/reports/{fileName}")
    public ResponseEntity<byte[]> downloadAgentReport(@PathVariable String fileName) {
        byte[] pdf = agentWorkspaceService.downloadPdfReport(config.getEmail(), fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(pdf);
    }

    @GetMapping("/api/agent/folders")
    public AgentFoldersResponse folders() {
        return agentWorkspaceService.folders(config.getEmail());
    }

    @PostMapping("/api/agent/folders")
    public AgentFolderDto createFolder(@RequestBody CreateAgentFolderRequest request) {
        return agentWorkspaceService.createFolder(config.getEmail(), request);
    }

    @PatchMapping("/api/agent/sessions/{id}")
    public AgentMutationResponse updateSession(@PathVariable String id, @RequestBody UpdateAgentSessionRequest request) {
        agentWorkspaceService.updateSession(config.getEmail(), id, request);
        return new AgentMutationResponse(true);
    }

    @DeleteMapping("/api/agent/sessions/{id}")
    public AgentMutationResponse deleteSession(@PathVariable String id) {
        agentWorkspaceService.deleteSession(config.getEmail(), id);
        return new AgentMutationResponse(true);
    }

    @PostMapping("/api/agent/files")
    public ResponseEntity<AgentFilesResponse> uploadAgentFiles(
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "files[]", required = false) MultipartFile[] bracketFiles,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "prompt", required = false) String prompt,
            @RequestParam(value = "documentType", required = false) DocumentType documentType) {
        return ResponseEntity.ok(agentWorkspaceService.uploadFiles(
                config.getEmail(),
                sessionId,
                mergeFiles(files, file, bracketFiles),
                documentType,
                prompt));
    }

    @PostMapping("/api/ai/context/user-profile-sync")
    public ResponseEntity<Map<String, Object>> syncUserProfileContext() {
        User user = currentUser();
        String response = aiService.syncUserProfileContext(user.getUserId());
        return ResponseEntity.ok(Map.of(
                "userId", user.getUserId(),
                "email", user.getEmail(),
                "target", "user-profile",
                "response", response));
    }

    @PostMapping("/api/ai/context/tax-profile-sync")
    public ResponseEntity<Map<String, Object>> syncTaxProfileContext() {
        User user = currentUser();
        String response = aiService.syncTaxProfileContext(user.getUserId());
        return ResponseEntity.ok(Map.of(
                "userId", user.getUserId(),
                "email", user.getEmail(),
                "target", "tax-profile",
                "response", response));
    }

    private User currentUser() {
        return userRepo.findByEmail(config.getEmail())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private MultipartFile[] mergeFiles(
            MultipartFile[] files,
            MultipartFile file,
            MultipartFile[] bracketFiles) {
        List<MultipartFile> mergedFiles = new ArrayList<>();

        if (files != null) {
            mergedFiles.addAll(Arrays.asList(files));
        }

        if (file != null) {
            mergedFiles.add(file);
        }

        if (bracketFiles != null) {
            mergedFiles.addAll(Arrays.asList(bracketFiles));
        }

        return mergedFiles.toArray(MultipartFile[]::new);
    }
}
