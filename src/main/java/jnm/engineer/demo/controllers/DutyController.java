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

import jnm.engineer.demo.services.DutyService;

/** Duty roster — anyone logged in may read; the head teacher or an admin set it (SecurityConfig). */
@RestController
@RequestMapping("/api/duty")
@RequiredArgsConstructor
public class DutyController {
    private final DutyService service;

    @GetMapping("/kinds")
    public ResponseEntity<List<Map<String, Object>>> kinds() { return ResponseEntity.ok(service.kinds()); }

    @PutMapping("/kinds")
    public ResponseEntity<Map<String, Object>> saveKind(@RequestBody DutyService.KindIn in) { return ResponseEntity.ok(service.saveKind(in)); }

    @GetMapping("/rota")
    public ResponseEntity<Map<String, Object>> rota(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.rota(from, to));
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestBody DutyService.GenerateIn in) { return ResponseEntity.ok(service.generate(in)); }

    @PostMapping("/assignments")
    public ResponseEntity<Map<String, Object>> assign(@RequestBody DutyService.AssignIn in) { return ResponseEntity.ok(service.assign(in)); }

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<Map<String, Object>> remove(@PathVariable Long id) { return ResponseEntity.ok(service.remove(id)); }

    /** Who is on duty in the week of a date (the daily record uses the teacher on duty). */
    @GetMapping("/on")
    public ResponseEntity<Map<String, Object>> on(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.onDate(date != null ? date : LocalDate.now()));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<Map<String, Object>>> mine(@AuthenticationPrincipal UserDetails me) { return ResponseEntity.ok(service.mine(me != null ? me.getUsername() : null)); }
}
