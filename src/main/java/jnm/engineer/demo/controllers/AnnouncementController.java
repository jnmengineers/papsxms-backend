package jnm.engineer.demo.controllers;

import jnm.engineer.demo.services.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Staff notices. Everyone: their notices, unread count, mark as read.
 * /admin/**: admin only (SecurityConfig) — post, edit, take down, see who has read.
 */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {
    private final AnnouncementService service;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> mine(@AuthenticationPrincipal UserDetails me) { return ResponseEntity.ok(service.mine(me.getUsername())); }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> unread(@AuthenticationPrincipal UserDetails me) { return ResponseEntity.ok(service.unreadCount(me.getUsername())); }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> read(@PathVariable Long id, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.markRead(id, me.getUsername()));
    }

    @GetMapping("/admin")
    public ResponseEntity<List<Map<String, Object>>> all() { return ResponseEntity.ok(service.all()); }

    @PostMapping("/admin")
    public ResponseEntity<Map<String, Object>> create(@RequestBody AnnouncementService.AnnouncementIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.save(null, in, me.getUsername()));
    }

    @PutMapping("/admin/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody AnnouncementService.AnnouncementIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.save(id, in, me.getUsername()));
    }

    @PostMapping("/admin/{id}/archive")
    public ResponseEntity<Map<String, Object>> archive(@PathVariable Long id) { return ResponseEntity.ok(service.setArchived(id, true)); }

    @PostMapping("/admin/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(@PathVariable Long id) { return ResponseEntity.ok(service.setArchived(id, false)); }

    @GetMapping("/admin/{id}/readers")
    public ResponseEntity<List<Map<String, Object>>> readers(@PathVariable Long id) { return ResponseEntity.ok(service.readers(id)); }
}
