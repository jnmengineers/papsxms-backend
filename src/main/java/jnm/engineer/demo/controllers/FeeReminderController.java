package jnm.engineer.demo.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jnm.engineer.demo.services.FeeReminderService;

/** Fee reminders — admin and bursar (SecurityConfig). */
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class FeeReminderController {
    private final FeeReminderService service;

    @GetMapping
    public ResponseEntity<Map<String, Object>> families(@RequestParam(required = false) Long classId, @RequestParam(required = false) BigDecimal minBalance) {
        return ResponseEntity.ok(service.families(classId, minBalance));
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> settings() { return ResponseEntity.ok(service.settings()); }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> saveSettings(@RequestBody FeeReminderService.SettingsIn in) { return ResponseEntity.ok(service.saveSettings(in)); }

    /** Send the reminder SMS to the chosen families (Africa's Talking). */
    @PostMapping("/send-sms")
    public ResponseEntity<Map<String, Object>> sendSms(@RequestBody FeeReminderService.SmsIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.sendSms(in, me != null ? me.getUsername() : null));
    }

    @PostMapping("/sent")
    public ResponseEntity<Map<String, Object>> sent(@RequestBody FeeReminderService.SentIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.markSent(in, me != null ? me.getUsername() : null));
    }
}
