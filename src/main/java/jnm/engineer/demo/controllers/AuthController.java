package jnm.engineer.demo.controllers;

import jnm.engineer.demo.dto.LoginRequest;
import jnm.engineer.demo.dto.LoginResponse;
import jnm.engineer.demo.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final jnm.engineer.demo.repositories.UserRepository userRepository;

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

        String token = jwtUtil.generateToken(username, role);

        return ResponseEntity.ok(new LoginResponse(token, role, username));

    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody LoginRequest request) {
        jnm.engineer.demo.models.User user = new jnm.engineer.demo.models.User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        // ✅ Make sure this line exists
        user.setRole(jnm.engineer.demo.models.User.Role.valueOf(request.getRole()));

        userRepository.save(user);
        return ResponseEntity.ok("User registered successfully");
    }
}