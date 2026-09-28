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
        return ResponseEntity.ok(visible(studentService.getAllStudents()));
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
        return ResponseEntity.ok(visible(studentService.searchByName(className)));
    }

    // GET /api/students/by-class/5
    @GetMapping("/by-class/{classId}")
    public ResponseEntity<List<Student>> getByClass(@PathVariable Long classId) {
        if (!accessGuard.currentScope().canRead(classId)) {
            throw AccessGuard.forbidden("You can only view students in your own classes.");
        }
        return ResponseEntity.ok(studentService.GetByClass(classId));
    }

    // GET /api/students/search?name=jane
    @GetMapping("/search")
    public ResponseEntity<List<Student>> search(@RequestParam String name) {
        return ResponseEntity.ok(visible(studentService.searchByName(name)));
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
