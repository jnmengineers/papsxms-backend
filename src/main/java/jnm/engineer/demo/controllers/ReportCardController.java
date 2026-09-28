package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.ReportCard;
import jnm.engineer.demo.security.AccessGuard;
import jnm.engineer.demo.services.ReportCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Report cards. Scoped by AccessGuard:
 *   ADMIN ........ everything
 *   TEACHER ...... READ cards for their own class + classes they teach a subject in;
 *                  CREATE / GENERATE / EDIT / DELETE cards for their OWN class only
 *                  (subject teachers enter marks, class teachers own the report card);
 *                  can never set or change the PRINCIPAL's comment
 *   CLERK ........ read only (SecurityConfig blocks clerks from changing report cards)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reportCards")
public class ReportCardController {
    private final ReportCardService reportCardService;
    private final AccessGuard accessGuard;

    // The card's JSON hides the student's class, so look it up from the student id
    private Long classOf(ReportCard card, Map<Long, Long> cache) {
        if (card == null || card.getStudent() == null) return null;
        Long studentId = card.getStudent().getStudentId();
        return cache.computeIfAbsent(studentId, accessGuard::classOfStudent);
    }

    private List<ReportCard> visible(List<ReportCard> cards) {
        AccessGuard.Scope scope = accessGuard.currentScope();
        if (scope.isAll()) return cards;
        Map<Long, Long> cache = new HashMap<>();
        return cards.stream().filter(c -> scope.canRead(classOf(c, cache))).toList();
    }

    private void requireRead(Long classId) {
        if (!accessGuard.currentScope().canRead(classId)) {
            throw AccessGuard.forbidden("You can only view report cards for your own classes.");
        }
    }

    /** Own class only (canWrite with no subject). Admin passes automatically. */
    private AccessGuard.Scope requireWrite(Long classId) {
        AccessGuard.Scope scope = accessGuard.currentScope();
        if (!scope.canWrite(classId, null)) {
            throw AccessGuard.forbidden("Only the class teacher can change report cards for this class.");
        }
        return scope;
    }

    // ── reading ──────────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<ReportCard>> getAll() {
        return ResponseEntity.ok(visible(reportCardService.getAllReportCards()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportCard> getById(@PathVariable Long id) {
        ReportCard card = reportCardService.getById(id);
        requireRead(classOf(card, new HashMap<>()));
        return ResponseEntity.ok(card);
    }

    @GetMapping("/by-student/{studentId}")
    public ResponseEntity<List<ReportCard>> getByStudent(@PathVariable Long studentId) {
        requireRead(accessGuard.classOfStudent(studentId));
        return ResponseEntity.ok(reportCardService.getByStudent(studentId));
    }

    @GetMapping("/by-exam/{examId}")
    public ResponseEntity<List<ReportCard>> getByExam(@PathVariable Long examId) {
        return ResponseEntity.ok(visible(reportCardService.getByExam(examId)));
    }

    @GetMapping("/student/{studentId}/exam/{examId}")
    public ResponseEntity<ReportCard> getByStudentAndExam(@PathVariable Long studentId, @PathVariable Long examId) {
        requireRead(accessGuard.classOfStudent(studentId));
        return ResponseEntity.ok(reportCardService.getByStudentAndExam(studentId, examId));
    }

    // ── changing ─────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ReportCard> create(@RequestBody ReportCard reportCard) {
        Long studentId = reportCard.getStudent() != null ? reportCard.getStudent().getStudentId() : null;
        AccessGuard.Scope scope = requireWrite(accessGuard.classOfStudent(studentId));
        if (!scope.isAll()) reportCard.setPrincipalComment(null);          // principal's words: admin only
        return ResponseEntity.status(HttpStatus.CREATED).body(reportCardService.create(reportCard));
    }

    @PostMapping("/generate/student/{studentId}/exam/{examId}")
    public ResponseEntity<ReportCard> generate(@PathVariable Long studentId, @PathVariable Long examId) {
        requireWrite(accessGuard.classOfStudent(studentId));
        return ResponseEntity.ok(reportCardService.generateFromResults(studentId, examId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReportCard> update(@PathVariable Long id, @RequestBody ReportCard reportCard) {
        ReportCard existing = reportCardService.getById(id);             // check the SAVED card, not the body
        AccessGuard.Scope scope = requireWrite(classOf(existing, new HashMap<>()));
        if (!scope.isAll()) {
            // Teachers can't change the principal's comment — keep whatever is saved
            reportCard.setPrincipalComment(existing.getPrincipalComment());
            // …or move the card to a different student / exam
            reportCard.setStudent(existing.getStudent());
            reportCard.setExam(existing.getExam());
        }
        return ResponseEntity.ok(reportCardService.update(id, reportCard));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        requireWrite(classOf(reportCardService.getById(id), new HashMap<>()));
        reportCardService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Report card deleted successfully"));
    }
}
