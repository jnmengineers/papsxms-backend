package jnm.engineer.demo.controllers;

import jakarta.validation.Valid;
import jnm.engineer.demo.models.Subject;
import jnm.engineer.demo.models.Teacher;
import jnm.engineer.demo.services.SubjectService;
import jnm.engineer.demo.services.TeacherServices;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subjects")
public class SubjectController {
    private final SubjectService subjectService;

    @GetMapping
    public ResponseEntity<List<Subject>> getAll(){
        return ResponseEntity.ok(subjectService.getAllSubjects());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Subject> getById(@PathVariable Long id){
        return ResponseEntity.ok(subjectService.getById(id));
    }

    //searching the teacher by name
    @GetMapping("/by-grade")
    public ResponseEntity<List<Subject>> getByGrade(@RequestParam String gradeLevel){
        return ResponseEntity.ok(subjectService.getByGradeLevel(gradeLevel));
    }

    @GetMapping("/by-teacher/{teacherId}")
    public ResponseEntity<List<Subject>> GetByTeacher(@PathVariable Long teacherId){
        return ResponseEntity.ok(subjectService.getByTeacher(teacherId));
    }


    // POST /api/users
    @PostMapping()
    public ResponseEntity<Subject> create(@Valid @RequestBody Subject subject){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subjectService.create(subject));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Subject> update(@PathVariable Long id,
                                       @Valid @RequestBody Subject subject){
        return ResponseEntity.ok(subjectService.update(id, subject));
    }
    // Assign teacher a subject
    @PatchMapping("/{subjectId}/assign-teacher/{teacherId}")
    public ResponseEntity<Subject> assignTeacher(@PathVariable Long subjectId,
                                                 @PathVariable Long teacherId){
        return ResponseEntity.ok(subjectService.assignTeacher(subjectId, teacherId));
    }


    //DELETE /api/users/1
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        subjectService.delete(id);
        return ResponseEntity.ok("user deleted successfully");
    }
}
