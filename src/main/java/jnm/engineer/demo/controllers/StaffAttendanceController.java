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

import jnm.engineer.demo.services.StaffAttendanceService;

/** Staff attendance. /mine/** — any logged-in staff member (own sign-in). The register — the office (SecurityConfig). */
@RestController
@RequestMapping("/api/staff-attendance")
@RequiredArgsConstructor
public class StaffAttendanceController {
    private final StaffAttendanceService service;

    @GetMapping("/day")
    public ResponseEntity<Map<String, Object>> day(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.day(date));
    }

    @PutMapping("/day")
    public ResponseEntity<Map<String, Object>> saveDay(@RequestBody StaffAttendanceService.DayIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.saveDay(in, me != null ? me.getUsername() : null));
    }

    @GetMapping("/month")
    public ResponseEntity<Map<String, Object>> month(@RequestParam int year, @RequestParam int month) { return ResponseEntity.ok(service.month(year, month)); }

    @GetMapping("/mine")
    public ResponseEntity<Map<String, Object>> mine(@AuthenticationPrincipal UserDetails me) { return ResponseEntity.ok(service.mine(me != null ? me.getUsername() : null)); }

    @PostMapping("/mine/sign-in")
    public ResponseEntity<Map<String, Object>> signIn(@AuthenticationPrincipal UserDetails me) { return ResponseEntity.ok(service.signIn(me != null ? me.getUsername() : null)); }

    @PostMapping("/mine/sign-out")
    public ResponseEntity<Map<String, Object>> signOut(@AuthenticationPrincipal UserDetails me) { return ResponseEntity.ok(service.signOut(me != null ? me.getUsername() : null)); }
}
