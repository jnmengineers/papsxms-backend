package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.*;
import jnm.engineer.demo.models.ExamTypeSetting;
import jnm.engineer.demo.models.GradeLevelSetting;
import jnm.engineer.demo.models.SchoolProfile;
import jnm.engineer.demo.models.SectionSetting;
import jnm.engineer.demo.models.StreamSetting;
import jnm.engineer.demo.services.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * School settings — web layer (rules in SettingsService).
 *   GET /api/settings/public   name, motto, logos — anyone (login page)
 *   GET /api/settings          everything — any logged-in user
 *   PUT /api/settings/...      change — ADMIN only (SecurityConfig: non-GET defaults to ADMIN)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService service;

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicInfo() { return ResponseEntity.ok(service.publicInfo()); }

    @GetMapping
    public ResponseEntity<Map<String, Object>> all() { return ResponseEntity.ok(service.all()); }

    @PutMapping("/profile")
    public ResponseEntity<SchoolProfile> profile(@RequestBody SettingsService.ProfileIn in) { return ResponseEntity.ok(service.saveProfile(in)); }

    @PutMapping("/sections")
    public ResponseEntity<List<SectionSetting>> sections(@RequestBody List<SettingsService.SectionIn> in) { return ResponseEntity.ok(service.saveSections(in)); }

    @PutMapping("/grades")
    public ResponseEntity<List<GradeLevelSetting>> grades(@RequestBody List<SettingsService.GradeIn> in) { return ResponseEntity.ok(service.saveGrades(in)); }

    @PutMapping("/streams")
    public ResponseEntity<List<StreamSetting>> streams(@RequestBody List<SettingsService.StreamIn> in) { return ResponseEntity.ok(service.saveStreams(in)); }

    @PutMapping("/exam-types")
    public ResponseEntity<List<ExamTypeSetting>> examTypes(@RequestBody List<SettingsService.ExamTypeIn> in) { return ResponseEntity.ok(service.saveExamTypes(in)); }
}
