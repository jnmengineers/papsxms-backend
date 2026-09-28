package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.UserRepository;
import jnm.engineer.demo.services.SchoolClassService;
import jnm.engineer.demo.services.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Classes. New in step 2:
 *  - assigning / removing a class teacher also updates that teacher's LOGIN, so the
 *    Users page and the Classes page can never disagree again
 *  - a class that still has students cannot be deleted (it used to delete the students!)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/classes")
public class SchoolClassController {
    private final SchoolClassService schoolClassService;
    private final StudentService studentService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<SchoolClass>> getAll() {
        return ResponseEntity.ok(schoolClassService.getAllSchoolClasses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SchoolClass> getById(@PathVariable Long id) {
        return ResponseEntity.ok(schoolClassService.getById(id));
    }

    @GetMapping("/by-name")
    public ResponseEntity<List<SchoolClass>> getBYClassName(@RequestParam String className) {
        return ResponseEntity.ok(schoolClassService.getBySchoolClassName(className));
    }

    @GetMapping("/by-teacher/{teacherId}")
    public ResponseEntity<List<SchoolClass>> getByTeacher(@PathVariable Long teacherId) {
        return ResponseEntity.ok(schoolClassService.getByClassTeacher(teacherId));
    }

    @PostMapping
    public ResponseEntity<SchoolClass> create(@RequestBody SchoolClass schoolClass) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schoolClassService.create(schoolClass));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SchoolClass> update(@PathVariable Long id, @RequestBody SchoolClass schoolClass) {
        return ResponseEntity.ok(schoolClassService.update(id, schoolClass));
    }

    /**
     * Makes the teacher class teacher. If this created a NEW login for them, the response
     * includes "newLogin": {username, temporaryPassword} — shown to the admin ONCE.
     */
    @PatchMapping("/{classId}/assign-teacher/{teacherId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> assignTeacher(@PathVariable Long classId, @PathVariable Long teacherId) {
        SchoolClassService.ClassTeacherResult result = schoolClassService.assignClassTeacher(classId, teacherId);
        SchoolClass cls = result.schoolClass();
        syncLogins(classId, cls, teacherId);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("schoolClass", cls);
        out.put("newLogin", result.temporaryPassword() == null ? null
                : Map.of("username", result.username(), "temporaryPassword", result.temporaryPassword()));
        return ResponseEntity.ok(out);
    }

    @PatchMapping("/{classId}/unassign-teacher")
    @Transactional
    public ResponseEntity<SchoolClass> unassignTeacher(@PathVariable Long classId) {
        SchoolClass cls = schoolClassService.unassignClassTeacher(classId);
        syncLogins(classId, cls, null);
        return ResponseEntity.ok(cls);
    }

    /**
     * Keeps teacher LOGINS in step with the class-teacher setting:
     *  - the new class teacher's login now manages this class
     *  - any other LINKED login that managed this class no longer does
     * (Logins not yet linked to a teacher record are left alone.)
     */
    private void syncLogins(Long classId, SchoolClass cls, Long newTeacherId) {
        for (User u : userRepository.findAll()) {
            if (u.getTeacher() == null) continue;
            Long tid = u.getTeacher().getTeacherId();
            boolean managesThis = u.getLinkedClass() != null && classId.equals(u.getLinkedClass().getClassId());
            if (newTeacherId != null && tid.equals(newTeacherId) && !managesThis) {
                u.setLinkedClass(cls);
                userRepository.save(u);
            } else if (managesThis && (newTeacherId == null || !tid.equals(newTeacherId))) {
                u.setLinkedClass(null);
                userRepository.save(u);
            }
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id) {
        int studentCount = studentService.GetByClass(id).size();
        if (studentCount > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "This class still has " + studentCount + " student(s). Move them to another class first."));
        }
        // Logins pointing at this class would block the delete — clear them first
        for (User u : userRepository.findAll()) {
            if (u.getLinkedClass() != null && id.equals(u.getLinkedClass().getClassId())) {
                u.setLinkedClass(null);
                userRepository.save(u);
            }
        }
        try {
            schoolClassService.delete(id);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "This class still has subjects, exam schedules or other records linked to it. Remove those first."));
        }
        return ResponseEntity.ok(Map.of("message", "Class deleted successfully"));
    }
}
