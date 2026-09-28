package jnm.engineer.demo.controllers;

import jnm.engineer.demo.services.TimetableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Teaching timetable. Reading: any logged-in user. Changes: admin (SecurityConfig default). */
@RestController
@RequestMapping("/api/timetable")
@RequiredArgsConstructor
public class TimetableController {
    private final TimetableService service;

    @GetMapping("/slots")
    public ResponseEntity<Map<String, Object>> slots() { return ResponseEntity.ok(service.slots()); }

    @PutMapping("/slots/{section}")
    public ResponseEntity<Map<String, Object>> saveSlots(@PathVariable String section, @RequestBody List<TimetableService.SlotIn> in) {
        return ResponseEntity.ok(service.saveSlots(section, in));
    }

    @GetMapping("/lessons/{grade}")
    public ResponseEntity<List<Map<String, Object>>> lessons(@PathVariable String grade) { return ResponseEntity.ok(service.lessons(grade)); }

    @PutMapping("/lessons/{grade}")
    public ResponseEntity<Map<String, Object>> saveLessons(@PathVariable String grade, @RequestBody List<TimetableService.LessonIn> in) {
        return ResponseEntity.ok(service.saveLessons(grade, in));
    }

    @GetMapping("/class/{classId}")
    public ResponseEntity<Map<String, Object>> classView(@PathVariable Long classId) { return ResponseEntity.ok(service.classView(classId)); }

    @PutMapping("/class/{classId}")
    public ResponseEntity<Map<String, Object>> saveClass(@PathVariable Long classId, @RequestBody List<TimetableService.EntryIn> in) {
        return ResponseEntity.ok(service.saveClass(classId, in));
    }

    @GetMapping("/busy")
    public ResponseEntity<List<Map<String, Object>>> busy(@RequestParam(required = false) Long excludeClassId) {
        return ResponseEntity.ok(service.busy(excludeClassId));
    }

    @GetMapping("/teacher/{teacherId}")
    public ResponseEntity<Map<String, Object>> teacher(@PathVariable Long teacherId) { return ResponseEntity.ok(service.teacherView(teacherId)); }

    @GetMapping("/mine")
    public ResponseEntity<Map<String, Object>> mine(@AuthenticationPrincipal UserDetails me) { return ResponseEntity.ok(service.mine(me.getUsername())); }
}
