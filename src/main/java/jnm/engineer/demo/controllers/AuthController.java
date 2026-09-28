package jnm.engineer.demo.controllers;

import jnm.engineer.demo.dto.LoginRequest;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.repositories.UserRepository;
import jnm.engineer.demo.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Login, user creation and password changes.
 *
 * Who may call what is set in SecurityConfig:
 *   /login            anyone
 *   /register         ADMIN only
 *   /change-password  any logged-in user (their OWN password only)
 *
 * (The old @CrossOrigin("*") was removed — CORS is handled once, in SecurityConfig.)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private static final int MIN_PASSWORD_LENGTH = 8;   // same rule as the React pages
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final SchoolClassRepository schoolClassRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String usernameIn = request.getUsername() == null ? "" : request.getUsername().trim();
        User account = userRepository.findByUsername(usernameIn).orElse(null);
        LocalDateTime now = LocalDateTime.now();

        // Locked after too many wrong passwords? Refuse without even checking the password.
        if (account != null && account.getLockedUntil() != null && account.getLockedUntil().isAfter(now)) {
            return lockedResponse(account.getLockedUntil(), now);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(usernameIn, request.getPassword()));
        } catch (BadCredentialsException e) {
            if (account != null) {
                int attempts = (account.getFailedLoginAttempts() == null ? 0 : account.getFailedLoginAttempts()) + 1;
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    account.setFailedLoginAttempts(0);
                    account.setLockedUntil(now.plus(LOCK_DURATION));
                    userRepository.save(account);
                    return lockedResponse(account.getLockedUntil(), now);
                }
                account.setFailedLoginAttempts(attempts);
                userRepository.save(account);
            }
            // Same message whether the username or the password is wrong — don't reveal which
            return error(HttpStatus.UNAUTHORIZED, "Incorrect username or password.");
        } catch (AuthenticationException e) {
            return error(HttpStatus.UNAUTHORIZED, "This account cannot log in. Contact the administrator.");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();
        String role = userDetails.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");

        Long linkedClassId = null;
        String linkedClassName = null;
        String linkedStream = null;

        User user = userRepository.findByUsername(username).orElse(null);

        // Successful login clears any earlier wrong attempts / expired lock
        if (user != null && ((user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0) || user.getLockedUntil() != null)) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        // Which class this login manages: the class-teacher setting wins; the login's own
        // linkedClass is used for logins not yet linked to a teacher record
        SchoolClass cls = user != null ? user.getLinkedClass() : null;
        if (user != null && user.getTeacher() != null) {
            List<SchoolClass> classTeacherOf = schoolClassRepository.findByClassTeacherTeacherId(user.getTeacher().getTeacherId());
            final Long current = cls != null ? cls.getClassId() : null;
            if (!classTeacherOf.isEmpty() && classTeacherOf.stream().noneMatch(c -> c.getClassId().equals(current))) {
                cls = classTeacherOf.get(0);
            }
        }
        if (cls != null) {
            linkedClassId = cls.getClassId();
            linkedClassName = cls.getClassName();
            linkedStream = cls.getStream();
        }

        String token = jwtUtil.generateToken(username, role);
        boolean mustChangePassword = user != null && user.isMustChangePassword();

        // Name to show on screen instead of the username (teacher usernames are phone numbers).
        // Teachers: their last name. Everyone else: their username.
        String lastName = (user != null && user.getTeacher() != null) ? user.getTeacher().getLastName() : null;
        String displayName = (lastName != null && !lastName.isBlank()) ? lastName.trim() : username;

        // Same fields as before (so nothing else changes) + firstName, lastName, displayName
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("role", role);
        response.put("username", username);
        response.put("linkedClassId", linkedClassId);
        response.put("linkedClassName", linkedClassName);
        response.put("linkedStream", linkedStream);
        response.put("mustChangePassword", mustChangePassword);
        response.put("firstName", user != null && user.getTeacher() != null ? user.getTeacher().getFirstName() : null);
        response.put("lastName", lastName);
        response.put("displayName", displayName);
        return ResponseEntity.ok(response);
    }

    /** ADMIN only (enforced in SecurityConfig). */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody LoginRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request.getPassword() == null ? "" : request.getPassword();

        if (username.isEmpty() || username.contains(" ")) {
            return error(HttpStatus.BAD_REQUEST, "Username is required and cannot contain spaces.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return error(HttpStatus.BAD_REQUEST, "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (password.equalsIgnoreCase(username)) {
            return error(HttpStatus.BAD_REQUEST, "Password cannot be the same as the username.");
        }

        // Role must be given explicitly — it no longer defaults to ADMIN
        User.Role role;
        try {
            role = User.Role.valueOf(request.getRole() == null ? "" : request.getRole().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "Role must be one of: ADMIN, TEACHER, CLERK, ACCOUNTANT.");
        }

        if (userRepository.findByUsername(username).isPresent()) {
            return error(HttpStatus.CONFLICT, "Username \"" + username + "\" is already taken.");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        // The admin chose this password, so the person must set their own on first login
        user.setMustChangePassword(true);

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    /**
     * Changes the password of the LOGGED-IN user (taken from the token).
     * Any "username" in the request body is ignored, so nobody can change
     * someone else's password.
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal UserDetails principal,
                                            @RequestBody Map<String, String> request) {
        if (principal == null) {
            return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
        }
        String currentPassword = request.getOrDefault("currentPassword", "");
        String newPassword = request.getOrDefault("newPassword", "");

        User user = userRepository.findByUsername(principal.getUsername()).orElse(null);
        if (user == null) {
            return error(HttpStatus.UNAUTHORIZED, "This account no longer exists.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            return error(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            return error(HttpStatus.BAD_REQUEST, "New password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (newPassword.equals(currentPassword)) {
            return error(HttpStatus.BAD_REQUEST, "New password must be different from the current one.");
        }
        if (newPassword.toLowerCase().contains(user.getUsername().toLowerCase())) {
            return error(HttpStatus.BAD_REQUEST, "New password must not contain your username.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        // Every login token issued before now stops working (other devices are logged out)…
        user.setPasswordChangedAt(LocalDateTime.now().withNano(0));
        userRepository.save(user);
        // …and this device gets a fresh token so the person stays logged in here.
        String freshToken = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully", "token", freshToken));
    }

    private ResponseEntity<Map<String, Object>> lockedResponse(LocalDateTime lockedUntil, LocalDateTime now) {
        long minutes = Math.max(1, (Duration.between(now, lockedUntil).getSeconds() + 59) / 60);
        return error(HttpStatus.TOO_MANY_REQUESTS,
                "Too many wrong passwords. This account is locked for " + minutes + " more minute"
                        + (minutes == 1 ? "" : "s") + ". Try again later, or ask the administrator.");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "message", message));
    }
}
