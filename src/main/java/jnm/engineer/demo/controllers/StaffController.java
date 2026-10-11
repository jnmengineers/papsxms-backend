package jnm.engineer.demo.controllers;

import jnm.engineer.demo.services.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Staff register — ADMIN; the chairperson may read (SecurityConfig). */
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {
    private final StaffService service;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() { return ResponseEntity.ok(service.list()); }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) { return ResponseEntity.ok(service.get(id)); }

    @PutMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody StaffService.StaffRecordIn in) { return ResponseEntity.ok(service.save(in)); }

    @PostMapping("/import-teachers")
    public ResponseEntity<Map<String, Object>> importTeachers() { return ResponseEntity.ok(service.importTeachers()); }
}
