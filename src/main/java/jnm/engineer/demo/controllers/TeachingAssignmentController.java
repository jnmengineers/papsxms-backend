package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.TeachingAssignment;
import jnm.engineer.demo.services.TeachingAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Subject-teacher assignments — web layer only (rules in TeachingAssignmentService).
 * Reading: any logged-in user. Changing: ADMIN only (SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teaching-assignments")
public class TeachingAssignmentController {

    private final TeachingAssignmentService service;

    @GetMapping
    public ResponseEntity<List<TeachingAssignment>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    /** The logged-in teacher's own class(es) and subject classes — used by Mark Entry. */
    @GetMapping("/mine")
    public ResponseEntity<List<Map<String, Object>>> mine(@AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.mine(me.getUsername()));
    }

    @GetMapping("/by-teacher/{teacherId}")
    public ResponseEntity<List<TeachingAssignment>> byTeacher(@PathVariable Long teacherId) {
        return ResponseEntity.ok(service.byTeacher(teacherId));
    }

    @GetMapping("/by-class/{classId}")
    public ResponseEntity<List<TeachingAssignment>> byClass(@PathVariable Long classId) {
        return ResponseEntity.ok(service.byClass(classId));
    }

    @PutMapping("/class/{classId}/subject/{subjectId}/teacher/{teacherId}")
    public ResponseEntity<TeachingAssignment> assign(@PathVariable Long classId, @PathVariable Long subjectId,
                                                     @PathVariable Long teacherId) {
        return ResponseEntity.ok(service.assign(classId, subjectId, teacherId));
    }

    @DeleteMapping("/class/{classId}/subject/{subjectId}")
    public ResponseEntity<Map<String, String>> remove(@PathVariable Long classId, @PathVariable Long subjectId) {
        service.remove(classId, subjectId);
        return ResponseEntity.ok(Map.of("message", "Assignment removed"));
    }
}
