package com.Accounting.app.AI;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.Accounting.app.AI.dto.AgentMemoryRequest;
import com.Accounting.app.auth.User;
import com.Accounting.app.auth.UserRepo;
import com.Accounting.app.exceptions.InvalidInputException;
import com.Accounting.app.exceptions.UserNotFoundException;

@Service
public class AgentMemoryService {
    private static final List<String> ALLOWED_MEMORY_TYPES = List.of(
            "PREFERENCE",
            "TAX_GOAL",
            "FINANCIAL_GOAL",
            "PERSONAL_CONTEXT",
            "SYSTEM_NOTE");

    private final AgentMemoryRepo agentMemoryRepo;
    private final UserRepo userRepo;

    public AgentMemoryService(AgentMemoryRepo agentMemoryRepo, UserRepo userRepo) {
        this.agentMemoryRepo = agentMemoryRepo;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public List<AgentMemory> getUserMemories(Integer userId) {
        return agentMemoryRepo.findByUser_IdOrderByUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<AgentMemory> getRelevantMemories(Integer userId) {
        return agentMemoryRepo.findByUser_IdOrderByUpdatedAtDesc(userId).stream()
                .filter(memory -> memory.getContent() != null && !memory.getContent().isBlank())
                .filter(memory -> memory.getConfidence() == null || memory.getConfidence() >= 0.5d)
                .limit(25)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentMemory saveMemory(Integer userId, String memoryType, String content) {
        AgentMemoryRequest request = new AgentMemoryRequest(null, memoryType, content, 1.0d);
        return createMemory(userId, request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentMemory createMemory(Integer userId, AgentMemoryRequest request) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        AgentMemory memory = new AgentMemory();
        memory.setUser(user);
        memory.setMemoryType(normalizeMemoryType(request.memoryType()));
        memory.setContent(requireContent(request.content()));
        memory.setConfidence(normalizeConfidence(request.confidence()));
        memory.setCreatedAt(LocalDateTime.now());
        memory.setUpdatedAt(LocalDateTime.now());

        return agentMemoryRepo.save(memory);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentMemory updateMemory(Integer userId, Integer memoryId, AgentMemoryRequest request) {
        AgentMemory memory = agentMemoryRepo.findByIdAndUser_Id(memoryId, userId)
                .orElseThrow(() -> new InvalidInputException("Agent memory not found"));

        memory.setMemoryType(normalizeMemoryType(request.memoryType()));
        memory.setContent(requireContent(request.content()));
        memory.setConfidence(normalizeConfidence(request.confidence()));
        memory.setUpdatedAt(LocalDateTime.now());
        return agentMemoryRepo.save(memory);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteMemory(Integer userId, Integer memoryId) {
        AgentMemory memory = agentMemoryRepo.findByIdAndUser_Id(memoryId, userId)
                .orElseThrow(() -> new InvalidInputException("Agent memory not found"));
        agentMemoryRepo.delete(memory);
    }

    private String normalizeMemoryType(String memoryType) {
        String normalized = memoryType == null ? "" : memoryType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_MEMORY_TYPES.contains(normalized)) {
            throw new InvalidInputException("Unsupported memory type");
        }
        return normalized;
    }

    private String requireContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            throw new InvalidInputException("Memory content is required");
        }
        return normalized;
    }

    private Double normalizeConfidence(Double confidence) {
        if (confidence == null) {
            return 1.0d;
        }
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new InvalidInputException("Confidence must be between 0.0 and 1.0");
        }
        return confidence;
    }
}
