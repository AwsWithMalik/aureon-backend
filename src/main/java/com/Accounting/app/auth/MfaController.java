package com.Accounting.app.auth;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.Accounting.app.exceptions.InvalidInputException;
import com.Accounting.app.exceptions.UserNotFoundException;

@RestController
@RequestMapping("/api/security/mfa")
public class MfaController {

    private final MfaService mfaService;
    private final Config config;
    private final UserRepo userRepo;

    public MfaController(MfaService mfaService, Config config, UserRepo userRepo) {
        this.mfaService = mfaService;
        this.config = config;
        this.userRepo = userRepo;
    }

    @PostMapping("/setup")
    public ResponseEntity<MfaSetupResponse> setupMfa() {
        Integer userId = currentUserId();
        return ResponseEntity.ok(mfaService.startSetup(userId));
    }

    @PostMapping("/enable")
    public ResponseEntity<?> enableMfa(@RequestBody Map<String, Object> request) {
        Integer userId = currentUserId();
        mfaService.enableMfa(userId, codeFrom(request));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/disable")
    public ResponseEntity<?> disableMfa(@RequestBody Map<String, Object> request) {
        Integer userId = currentUserId();
        mfaService.disableMfa(userId, codeFrom(request));
        return ResponseEntity.ok().build();
    }

    private Integer currentUserId() {
        return userRepo.findByEmail(config.getEmail())
                .orElseThrow(() -> new UserNotFoundException("User not found"))
                .getUserId();
    }

    private String codeFrom(Map<String, Object> request) {
        String code = valueFrom(request, "code", "mfaCode", "totpCode", "verificationCode");
        if (code == null || code.isBlank()) {
            throw new InvalidInputException("MFA code is required.");
        }
        return code;
    }

    private String valueFrom(Map<String, Object> request, String... keys) {
        if (request == null) {
            return null;
        }
        for (String key : keys) {
            Object value = request.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }
}
