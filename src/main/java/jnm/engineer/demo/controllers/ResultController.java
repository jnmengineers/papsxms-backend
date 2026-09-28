package jnm.engineer.demo.controllers;

import jnm.engineer.demo.dto.BulkResultRequest;
import jnm.engineer.demo.dto.BulkResultResponse;
import jnm.engineer.demo.models.Result;
import jnm.engineer.demo.security.AccessGuard;
import jnm.engineer.demo.services.ResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Marks. Every endpoint now checks the logged-in user's scope (see AccessGuard):
 *   - lists are filtered to the classes the user may see
 *   - single items / students outside the user's classes → 403
 *   - saving a mark needs write access to that class AND subject
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/results")
public class ResultController {
    private final ResultService resultService;
    private final AccessGuard accessGuard;

    // ── helpers ──────────────────────────────────────────────────────────────
    private static Long classOf(Result r) {
        return (r != null && r.getStudent() != null && r.getStudent().getSchoolClass() != null)
                ? r.getStudent().getSchoolClass().getClassId() : null;
    }

    private static Long subjectOf(Result r) {
        return (r != null && r.getSubject() != null) ? r.getSubject().getSubjectId() : null;
    }

    private List<Result> visible(List<Result> all, AccessGuard.Scope scope) {
        return scope.isAll() ? all : all.stream().filter(r -> scope.canRead(classOf(r))).toList();
    }

    private void requireReadStudent(AccessGuard.Scope scope, Long studentId) {
        if (!scope.canRead(accessGuard.classOfStudent(studentId))) {
            throw AccessGuard.forbidden("You can only view marks for your own classes.");
        }
    }

    // ── reading ──────────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<Result>> getAll() {
        return ResponseEntity.ok(visible(resultService.getAllResults(), accessGuard.currentScope()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result> getById(@PathVariable Long id) {
        Result r = resultService.getById(id);
        if (!accessGuard.currentScope().canRead(classOf(r))) {
            throw AccessGuard.forbidden("You can only view marks for your own classes.");
        }
        return ResponseEntity.ok(r);
    }

    @GetMapping("/by-student/{studentId}")
    public ResponseEntity<List<Result>> getByStudent(@PathVariable Long studentId) {
        requireReadStudent(accessGuard.currentScope(), studentId);
        return ResponseEntity.ok(resultService.getByStudent(studentId));
    }

    @GetMapping("/by-exam/{examId}")
    public ResponseEntity<List<Result>> getByExam(@PathVariable Long examId) {
        return ResponseEntity.ok(visible(resultService.getByExam(examId), accessGuard.currentScope()));
    }

    @GetMapping("/student/{studentId}/exam/{examId}")
    public ResponseEntity<List<Result>> getByStudentAndExam(@PathVariable Long studentId, @PathVariable Long examId) {
        requireReadStudent(accessGuard.currentScope(), studentId);
        return ResponseEntity.ok(resultService.getByStudentAndExam(studentId, examId));
    }

    @GetMapping("/progressive/student/{studentId}/term/{term}/year/{academicYear}")
    public ResponseEntity<Map<String, Object>> getProgressiveResults(@PathVariable Long studentId,
                                                                     @PathVariable Integer term,
                                                                     @PathVariable String academicYear) {
        requireReadStudent(accessGuard.currentScope(), studentId);
        return ResponseEntity.ok(resultService.getProgressiveResults(studentId, term, academicYear));
    }

    @GetMapping("/progressive/student/{studentId}/upto-exam/{examId}")
    public ResponseEntity<Map<String, Object>> getProgressiveResultsUpToExam(@PathVariable Long studentId,
                                                                             @PathVariable Long examId) {
        requireReadStudent(accessGuard.currentScope(), studentId);
        return ResponseEntity.ok(resultService.getProgressiveResultsUpToExam(studentId, examId));
    }

    @GetMapping("/progressive/class/{className}/term/{term}/year/{academicYear}/improvements")
    public ResponseEntity<List<Map<String, Object>>> getMostImproved(@PathVariable String className,
                                                                     @PathVariable Integer term,
                                                                     @PathVariable String academicYear) {
        if (!accessGuard.canReadClassName(accessGuard.currentScope(), className)) {
            throw AccessGuard.forbidden("You can only view your own classes.");
        }
        return ResponseEntity.ok(resultService.getMostImprovedStudents(className, term, academicYear));
    }

    // ── changing ─────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Result> create(@Valid @RequestBody Result result) {
        Long studentId = result.getStudent() != null ? result.getStudent().getStudentId() : null;
        Long classId = accessGuard.classOfStudent(studentId);        // looked up — never trust the body's class
        if (!accessGuard.currentScope().canWrite(classId, subjectOf(result))) {
            throw AccessGuard.forbidden("You can't enter marks for this class or subject.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(resultService.create(result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Result> update(@PathVariable Long id, @RequestBody Result result) {
        Result existing = resultService.getById(id);                 // check the SAVED mark, not the body
        if (!accessGuard.currentScope().canWrite(classOf(existing), subjectOf(existing))) {
            throw AccessGuard.forbidden("You can't change marks for this class or subject.");
        }
        return ResponseEntity.ok(resultService.update(id, result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        Result existing = resultService.getById(id);
        if (!accessGuard.currentScope().canWrite(classOf(existing), subjectOf(existing))) {
            throw AccessGuard.forbidden("You can't delete marks for this class or subject.");
        }
        resultService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Result deleted successfully"));
    }

    /**
     * Bulk save: EVERY mark in the batch is checked first. If any one is outside the
     * user's scope, nothing is saved (no half-saved batches).
     */
    @PostMapping("/bulk-save")
    public ResponseEntity<BulkResultResponse> bulkSave(@RequestBody BulkResultRequest request) {
        AccessGuard.Scope scope = accessGuard.currentScope();
        if (!scope.isAll() && request.getResults() != null) {
            Map<Long, Long> classCache = new HashMap<>();
            int denied = 0;
            for (var entry : request.getResults()) {
                Long classId = classCache.computeIfAbsent(entry.getStudentId(), accessGuard::classOfStudent);
                if (!scope.canWrite(classId, entry.getSubjectId())) denied++;
            }
            if (denied > 0) {
                throw AccessGuard.forbidden(denied + " of these marks are for a class or subject you don't teach. Nothing was saved.");
            }
        }
        return ResponseEntity.ok(resultService.bulkSave(request));
    }
}
