package com.pvr.primenaturals.controller;

import com.pvr.primenaturals.dto.request.LoginRequest;
import com.pvr.primenaturals.dto.request.SignupRequest;
import com.pvr.primenaturals.dto.response.JwtResponse;
import com.pvr.primenaturals.dto.response.MessageResponse;
import com.pvr.primenaturals.dto.request.ForgotPasswordRequest;
import com.pvr.primenaturals.dto.request.ResetPasswordRequest;
import com.pvr.primenaturals.entity.Role;
import com.pvr.primenaturals.entity.User;
import com.pvr.primenaturals.repository.UserRepository;
import com.pvr.primenaturals.security.JwtUtils;
import com.pvr.primenaturals.security.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import com.pvr.primenaturals.service.EmailService;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = Logger.getLogger(AuthController.class.getName());
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    EmailService emailService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        return ResponseEntity.ok(new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getName(),
                roles));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user's account
        User user = new User();
        user.setName(signUpRequest.getName());
        user.setEmail(signUpRequest.getEmail());
        user.setPassword(encoder.encode(signUpRequest.getPassword()));
        
        // Auto assign USER role for self-registration
        user.setRole(Role.USER);

        // If email is admin, make them ADMIN (for testing ease)
        if(signUpRequest.getEmail().contains("admin")) {
            user.setRole(Role.ADMIN);
        }

        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    @Transactional
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            String trimmedEmail = request.getEmail().trim().toLowerCase();
            log.info("[AUTH] Password reset requested for: " + trimmedEmail);

            User user = userRepository.findByEmail(trimmedEmail).orElse(null);
            
            if (user == null) {
                log.info("[AUTH] No account found for: " + trimmedEmail + " - returning generic response.");
                return ResponseEntity.ok(new MessageResponse("If your email is in our system, you will receive a reset link shortly."));
            }

            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
            // saveAndFlush() forces an immediate DB commit before the email is sent
            // This prevents a race condition where the user clicks the link before the token is persisted
            userRepository.saveAndFlush(user);
            log.info("[AUTH] Reset token persisted to DB for: " + trimmedEmail);

            String resetLink = "http://localhost:5173/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(trimmedEmail, resetLink);
            log.info("[AUTH] Reset email dispatched to: " + trimmedEmail);

            return ResponseEntity.ok(new MessageResponse("Recovery link dispatched to your secure terminal."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    @Transactional
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            String trimmedToken = request.getToken().trim();
            log.info("[AUTH] Password reset attempt with token: " + trimmedToken);

            Optional<User> userOptional = userRepository.findByResetToken(trimmedToken);
            
            if (userOptional.isEmpty()) {
                log.warning("[AUTH] No user found for token: " + trimmedToken);
                return ResponseEntity.badRequest().body(new MessageResponse("Security link is invalid or has already been used."));
            }

            User user = userOptional.get();

            if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
                log.warning("[AUTH] Token expired for user: " + user.getEmail());
                return ResponseEntity.badRequest().body(new MessageResponse("Security link has expired. Please request a new one from the Forgot Password page."));
            }

            user.setPassword(encoder.encode(request.getNewPassword()));
            user.setResetToken(null);
            user.setResetTokenExpiry(null);
            userRepository.saveAndFlush(user);
            log.info("[AUTH] Password updated successfully for: " + user.getEmail());

            return ResponseEntity.ok(new MessageResponse("Credentials updated. You may now re-authenticate."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(new MessageResponse("An internal anomaly occurred: " + e.getMessage()));
        }
    }
}
