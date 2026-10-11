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

import jnm.engineer.demo.services.GuardianService;

/** Parents / guardians — admin, clerk, bursar (SecurityConfig). */
@RestController
@RequestMapping("/api/guardians")
@RequiredArgsConstructor
public class GuardianController {
    private final GuardianService service;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() { return ResponseEntity.ok(service.list()); }

    @PutMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody GuardianService.GuardianIn in) { return ResponseEntity.ok(service.save(in)); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) { return ResponseEntity.ok(service.delete(id)); }

    /** Excel template: every learner without a parent, with empty parent columns to fill in. */
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Parents to fill in.xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.template());
    }

    /** Upload the filled template (or any Excel/CSV with the same headings). commit=false = check only. */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importFile(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                                          @RequestParam(defaultValue = "false") boolean commit) throws java.io.IOException {
        if (file.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Choose the file to upload.");
        if (file.getSize() > 5L * 1024 * 1024) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "The file is larger than 5 MB.");
        return ResponseEntity.ok(service.importFile(file.getOriginalFilename(), file.getBytes(), commit));
    }

    /** A learner's parents and siblings. */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<Map<String, Object>> forStudent(@PathVariable Long studentId) { return ResponseEntity.ok(service.forStudent(studentId)); }
}
