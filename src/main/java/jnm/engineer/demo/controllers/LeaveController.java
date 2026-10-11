package jnm.engineer.demo.controllers;

import jnm.engineer.demo.services.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Staff leave. /api/leave/mine/** — any logged-in staff member, own leave only.
 * Everything else — ADMIN (the chairperson may read). See SecurityConfig.
 */
@RestController
@RequestMapping("/api/leave")
@RequiredArgsConstructor
public class LeaveController {
    private final LeaveService service;
    public record NoteIn(String note, String otherName) {}

    private static String who(UserDetails me) { return me != null ? me.getUsername() : null; }

    // ── My leave ──
    @GetMapping("/mine")
    public ResponseEntity<Map<String, Object>> mine(@RequestParam(required = false) Integer year, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.mine(who(me), year != null ? year : LocalDate.now().getYear()));
    }

    @PostMapping("/mine")
    public ResponseEntity<Map<String, Object>> applyMine(@RequestBody LeaveService.LeaveIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.applyMine(in, who(me)));
    }

    @PostMapping("/mine/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelMine(@PathVariable Long id, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.cancel(id, who(me), false, null));
    }

    @GetMapping("/mine/days")
    public ResponseEntity<Map<String, Object>> daysMine(@RequestParam String type, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(service.preview(type, start, end));
    }

    // ── Office ──
    @GetMapping("/today")
    public ResponseEntity<Map<String, Object>> today() { return ResponseEntity.ok(service.today()); }

    @GetMapping("/requests")
    public ResponseEntity<List<Map<String, Object>>> requests(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                              @RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.requests(from, to, status));
    }

    @PostMapping("/requests")
    public ResponseEntity<Map<String, Object>> record(@RequestBody LeaveService.LeaveIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.record(in, who(me)));
    }

    @PostMapping("/requests/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id, @RequestBody(required = false) NoteIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.decide(id, true, in == null ? null : in.note(), in == null ? null : in.otherName(), who(me)));
    }

    @PostMapping("/requests/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id, @RequestBody NoteIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.decide(id, false, in == null ? null : in.note(), who(me)));
    }

    @PostMapping("/requests/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long id, @RequestBody(required = false) NoteIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.cancel(id, who(me), true, in == null ? null : in.note()));
    }

    @GetMapping("/balances")
    public ResponseEntity<List<Map<String, Object>>> balances(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(service.balances(year != null ? year : LocalDate.now().getYear()));
    }

    @GetMapping("/days")
    public ResponseEntity<Map<String, Object>> days(@RequestParam String type, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(service.preview(type, start, end));
    }

    // ── Settings ──
    @GetMapping("/types")
    public ResponseEntity<List<Map<String, Object>>> types() { return ResponseEntity.ok(service.types()); }

    @PutMapping("/types")
    public ResponseEntity<Map<String, Object>> saveType(@RequestBody LeaveService.TypeIn in) { return ResponseEntity.ok(service.saveType(in)); }

    @GetMapping("/holidays")
    public ResponseEntity<List<Map<String, Object>>> holidays(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(service.holidayList(year != null ? year : LocalDate.now().getYear()));
    }

    @PostMapping("/holidays")
    public ResponseEntity<Map<String, Object>> addHoliday(@RequestBody LeaveService.HolidayIn in) { return ResponseEntity.ok(service.addHoliday(in)); }

    @DeleteMapping("/holidays/{date}")
    public ResponseEntity<Map<String, Object>> removeHoliday(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.removeHoliday(date));
    }
}
