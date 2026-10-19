package jnm.engineer.demo.controllers;

import jakarta.validation.Valid;
import jnm.engineer.demo.models.Student;
import jnm.engineer.demo.security.AccessGuard;
import jnm.engineer.demo.services.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Students. Scoped by AccessGuard:
 *   ADMIN / CLERK ... everything (deleting is ADMIN only — SecurityConfig)
 *   TEACHER ......... sees students in their own class + classes they teach a subject in;
 *                     can add / edit students in their OWN class only;
 *                     cannot move students between classes (admin job)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/students")
public class StudentController {
    private final StudentService studentService;
    private final AccessGuard accessGuard;
    private final jnm.engineer.demo.services.FinanceService financeService;

    private static Long classOf(Student s) {
        return (s != null && s.getSchoolClass() != null) ? s.getSchoolClass().getClassId() : null;
    }

    private List<Student> visible(List<Student> list) {
        AccessGuard.Scope scope = accessGuard.currentScope();
        return scope.isAll() ? list : list.stream().filter(s -> scope.canRead(classOf(s))).toList();
    }

    // GET /api/students
    @GetMapping
    public ResponseEntity<List<Student>> getAll() {
        return ResponseEntity.ok(inSchool(visible(studentService.getAllStudents())));
    }

    // ── Learners who have left (transferred, left, graduated) — kept with their marks and fees ──
    public record LeaveIn(String status, java.time.LocalDate dateLeft, String reason, String transferSchool, Boolean consent) {}
    private static final java.util.Set<String> LEAVE_STATUSES = java.util.Set.of("TRANSFERRED", "DISAPPEARED", "LEFT", "GRADUATED");

    /** Only learners still in school (everyday lists: classes, mark entry, report cards, promotion). */
    private static List<Student> inSchool(List<Student> list) {
        return list.stream().filter(Student::isInSchool).collect(java.util.stream.Collectors.toList());
    }

    // GET /api/students/former — learners who have left
    @GetMapping("/former")
    public ResponseEntity<List<java.util.Map<String, Object>>> former() {
        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        visible(studentService.getAllStudents()).stream().filter(s -> !s.isInSchool())
                .sorted(java.util.Comparator.comparing(Student::getDateLeft, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .forEach(s -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("student", s);
                    m.put("balanceNow", financeService.balanceOf(s.getStudentId()));   // follow-up of arrears
                    out.add(m);
                });
        return ResponseEntity.ok(out);
    }

    // PUT /api/students/5/leave  {status: TRANSFERRED|LEFT|GRADUATED, dateLeft, reason, transferSchool}
    @PutMapping("/{id}/leave")
    public ResponseEntity<Student> leave(@PathVariable Long id, @RequestBody LeaveIn in) {
        Student s = studentService.getById(id);
        if (!accessGuard.currentScope().canWrite(classOf(s), null)) throw AccessGuard.forbidden("You can only change students in your own classes.");
        String st = in == null || in.status() == null ? "" : in.status().trim().toUpperCase();
        if (!LEAVE_STATUSES.contains(st)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Choose Transferred, Disappeared, Left or Graduated.");
        java.time.LocalDate d = in.dateLeft() != null ? in.dateLeft() : java.time.LocalDate.now();
        if (d.isAfter(java.time.LocalDate.now())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "The date left can't be in the future.");
        s.setStatus(st);
        s.setDateLeft(d);
        s.setLeftReason(in.reason() == null || in.reason().isBlank() ? null : in.reason().trim());
        s.setTransferSchool("TRANSFERRED".equals(st) && in.transferSchool() != null && !in.transferSchool().isBlank() ? in.transferSchool().trim() : null);
        s.setLeaveConsent("DISAPPEARED".equals(st) ? Boolean.FALSE : Boolean.TRUE.equals(in.consent()));
        s.setBalanceAtLeaving(financeService.balanceOf(s.getStudentId()));   // snapshot: what was owed on leaving
        return ResponseEntity.ok(studentService.save(s));
    }

    // PUT /api/students/5/reinstate — back in school
    @PutMapping("/{id}/reinstate")
    public ResponseEntity<Student> reinstate(@PathVariable Long id) {
        Student s = studentService.getById(id);
        if (!accessGuard.currentScope().canWrite(classOf(s), null)) throw AccessGuard.forbidden("You can only change students in your own classes.");
        s.setStatus(null); s.setDateLeft(null); s.setLeftReason(null); s.setTransferSchool(null);
        s.setLeaveConsent(null); s.setBalanceAtLeaving(null);
        return ResponseEntity.ok(studentService.save(s));
    }

    // GET /api/students/5
    @GetMapping("/{id}")
    public ResponseEntity<Student> getById(@PathVariable Long id) {
        Student s = studentService.getById(id);
        if (!accessGuard.currentScope().canRead(classOf(s))) {
            throw AccessGuard.forbidden("You can only view students in your own classes.");
        }
        return ResponseEntity.ok(s);
    }

    // GET /api/students/by-class-name?className=G4
    // TODO (unchanged): still calls searchByName(), which searches STUDENT names, not class names.
    @GetMapping("/by-class-name")
    public ResponseEntity<List<Student>> getByClassName(@RequestParam String className) {
        return ResponseEntity.ok(inSchool(visible(studentService.searchByName(className))));
    }

    // GET /api/students/for-marking/5 — any teacher: just what's needed to enter marks (invigilation mode).
    // No other details; saving marks is still checked (empty marks only) by ResultController.
    @GetMapping("/for-marking/{classId}")
    public ResponseEntity<List<java.util.Map<String, Object>>> forMarking(@PathVariable Long classId) {
        return ResponseEntity.ok(inSchool(studentService.GetByClass(classId)).stream().map(s -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("studentId", s.getStudentId());
            m.put("firstName", s.getFirstName());
            m.put("lastName", s.getLastName());
            m.put("admissionNumber", s.getAdmissionNumber());
            m.put("gender", s.getGender());
            m.put("schoolClass", java.util.Map.of("classId", classId));
            return m;
        }).toList());
    }

    // GET /api/students/by-class/5
    @GetMapping("/by-class/{classId}")
    public ResponseEntity<List<Student>> getByClass(@PathVariable Long classId) {
        if (!accessGuard.currentScope().canRead(classId)) {
            throw AccessGuard.forbidden("You can only view students in your own classes.");
        }
        return ResponseEntity.ok(inSchool(studentService.GetByClass(classId)));
    }

    // GET /api/students/search?name=jane
    @GetMapping("/search")
    public ResponseEntity<List<Student>> search(@RequestParam String name) {
        return ResponseEntity.ok(inSchool(visible(studentService.searchByName(name))));
    }

    // POST /api/students?classId=5
    @PostMapping
    public ResponseEntity<Student> create(@Valid @RequestBody Student student,
                                          @RequestParam(required = false) Long classId) {
        AccessGuard.Scope scope = accessGuard.currentScope();
        // Teachers: only into their own class (canWrite with no subject = own class only)
        if (!scope.isAll() && !scope.canWrite(classId, null)) {
            throw AccessGuard.forbidden("You can only add students to your own class.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.create(student, classId));
    }

    // PUT /api/students/5
    @PutMapping("/{id}")
    public ResponseEntity<Student> update(@PathVariable Long id, @Valid @RequestBody Student student) {
        AccessGuard.Scope scope = accessGuard.currentScope();
        if (!scope.isAll()) {
            Long currentClass = classOf(studentService.getById(id));          // the saved record, not the body
            if (!scope.canWrite(currentClass, null)) {
                throw AccessGuard.forbidden("You can only edit students in your own class.");
            }
            Long requestedClass = classOf(student);
            if (requestedClass != null && !requestedClass.equals(currentClass)) {
                throw AccessGuard.forbidden("Only the administrator can move a student to another class.");
            }
        }
        return ResponseEntity.ok(studentService.update(id, student));
    }

    // DELETE /api/students/5   (ADMIN only — enforced in SecurityConfig)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            studentService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Student deleted successfully"));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "This student still has marks or report cards. Delete those first, or move the student instead."));
        }
    }

    // PUT /api/students/5/move-class/7   (admin only — promotions and transfers)
    @PutMapping("/{studentId}/move-class/{classId}")
    public ResponseEntity<Student> moveToClass(@PathVariable Long studentId, @PathVariable Long classId) {
        if (!accessGuard.currentScope().isAll()) {
            throw AccessGuard.forbidden("Only the administrator can move a student to another class.");
        }
        return ResponseEntity.ok(studentService.moveToClass(studentId, classId));
    }
}