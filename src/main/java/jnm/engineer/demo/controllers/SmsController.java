package jnm.engineer.demo.controllers;

import jnm.engineer.demo.services.GuardianService;
import jnm.engineer.demo.services.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** SMS account settings, a test message and the log — ADMIN only (SecurityConfig). */
@RestController
@RequestMapping("/api/sms")
@RequiredArgsConstructor
public class SmsController {
    private final SmsService service;
    public record TestIn(String phone) {}

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> settings() { return ResponseEntity.ok(service.settings()); }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> save(@RequestBody SmsService.SettingsIn in) { return ResponseEntity.ok(service.saveSettings(in)); }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test(@RequestBody TestIn in, @AuthenticationPrincipal UserDetails me) {
        String phone = GuardianService.phone(in == null ? null : in.phone());
        if (phone == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Enter a phone number for the test.");
        SmsService.Result r = service.send(List.of(new SmsService.Outgoing(phone, "Test message from the school system. If you received this, SMS is working.", null)), me != null ? me.getUsername() : null).get(0);
        return ResponseEntity.ok(Map.of("ok", r.ok(), "message", r.ok() ? "Test SMS sent to " + phone + (r.cost() != null ? " (cost " + r.cost() + ")" : "") + "." : r.error()));
    }

    @GetMapping("/log")
    public ResponseEntity<List<Map<String, Object>>> log() { return ResponseEntity.ok(service.log()); }
}
