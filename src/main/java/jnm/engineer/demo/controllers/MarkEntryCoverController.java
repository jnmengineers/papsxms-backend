package jnm.engineer.demo.controllers;

import jnm.engineer.demo.services.MarkEntryCoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Covering for a colleague (marks only) — web layer (rules in MarkEntryCoverService).
 *   GET  /api/mark-cover              active covers I can see
 *   GET  /api/mark-cover/colleagues   teachers who can be chosen
 *   POST /api/mark-cover              arrange a cover
 *   POST /api/mark-cover/{id}/cancel  cancel / hand back
 * Changes allowed for ADMIN and TEACHER (SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mark-cover")
public class MarkEntryCoverController {

    private final MarkEntryCoverService service;

    private static boolean isAdmin(UserDetails me) {
        return me.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> active(@AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.active(me.getUsername(), isAdmin(me)));
    }

    @GetMapping("/colleagues")
    public ResponseEntity<List<Map<String, Object>>> colleagues(@AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.colleagues(me.getUsername()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> grant(@RequestBody MarkEntryCoverService.GrantIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.grant(in, me.getUsername(), isAdmin(me)));
    }

    @PostMapping("/{coverId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long coverId, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.cancel(coverId, me.getUsername(), isAdmin(me)));
    }
}
