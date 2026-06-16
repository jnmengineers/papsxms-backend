package jnm.engineer.demo.controllers;

import jnm.engineer.demo.dto.LoginRequest;
import jnm.engineer.demo.dto.LoginResponse;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.UserRepository;
import jnm.engineer.demo.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();
        String role = userDetails.getAuthorities()
                .iterator().next().getAuthority()
                .replace("ROLE_", "");

        // Get linked class for teacher
        Long linkedClassId = null;
        String linkedClassName = null;

        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null && user.getLinkedClass() != null) {
            linkedClassId = user.getLinkedClass().getClassId();
            linkedClassName = user.getLinkedClass().getClassName();
        }

        String token = jwtUtil.generateToken(username, role);

        return ResponseEntity.ok(new LoginResponse(token, role, username, linkedClassId, linkedClassName));
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody LoginRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        if (request.getRole() != null && !request.getRole().isEmpty()) {
            user.setRole(User.Role.valueOf(request.getRole()));
        } else {
            user.setRole(User.Role.ADMIN);
        }

        userRepository.save(user);
        return ResponseEntity.ok("User registered successfully");
    }
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            return ResponseEntity.status(400).body("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok("Password changed successfully");
    }
}