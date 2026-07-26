package com.Accounting.app.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.Accounting.app.auth.dto.AuthRegister;
import com.Accounting.app.auth.dto.LoginRequest;
import com.Accounting.app.auth.dto.LoginResult;
import com.Accounting.app.auth.dto.RegisterRequest;
import com.Accounting.app.auth.dto.UserDto;
import com.Accounting.app.exceptions.InvalidInputException;
import com.Accounting.app.exceptions.UserNotFoundException;

@Service
public class AuthService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepo userRepo, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthRegister register(RegisterRequest request) {
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User newUser = new User();
        newUser.setEmail(request.getEmail());
        newUser.setLoginPassword(hashedPassword);
        newUser.setName(request.getName());
        User savedUser = userRepo.save(newUser);

        UserDto response = new UserDto(
                savedUser.getUserId(),
                savedUser.getEmail(),
                savedUser.getName());

        return new AuthRegister(response);
    }

    public LoginResult login(LoginRequest request) {
        User existing = userRepo.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        boolean matches = passwordEncoder.matches(
                request.getPassword(),
                existing.getLoginPassword());

        if (!matches) {
            throw new InvalidInputException("Invalid email or password.");
        }

        if (Boolean.TRUE.equals(existing.getMfaEnabled())) {
            String mfaToken = jwtService.generateMfaToken(existing.getEmail());

            return new LoginResult(
                    "MFA_REQUIRED",
                    existing.getEmail(),
                    mfaToken);
        }

        return new LoginResult(
                "AUTHENTICATED",
                existing.getEmail(),
                null);
    }

    public Boolean isValidLogin(String email, String incomingToken) {
        Boolean token = jwtService.isTokenValid(incomingToken, email);
        return token;
    }

    public UserDto currentUser(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return new UserDto(user.getUserId(), user.getEmail(), user.getName());
    }

}
