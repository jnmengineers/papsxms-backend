package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.ClassSubject;
import jnm.engineer.demo.services.ClassSubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/class-subjects")
public class ClassSubjectController {

    private final ClassSubjectService classSubjectService;

    @GetMapping
    public ResponseEntity<List<ClassSubject>> getAll(){
        return ResponseEntity.ok(classSubjectService.getAllAssignments());
    }

    @GetMapping("/by-class/{classId}")
    public ResponseEntity<List<ClassSubject>> getByClass(@PathVariable Long classId){
        return ResponseEntity.ok(classSubjectService.getSubjectsByClass(classId));
    }

    @GetMapping("/by-subject/{subjectId}")
    public ResponseEntity<List<ClassSubject>> getBySubject(@PathVariable Long subjectId){
        return ResponseEntity.ok(classSubjectService.getClassesBySubject(subjectId));
    }

    @PostMapping("/assign/class/{classId}/subject/{subjectId}")
    public ResponseEntity<ClassSubject> assign(@PathVariable Long classId,
                                               @PathVariable Long subjectId){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(classSubjectService.assignSubjectToClass(classId, subjectId));
    }

    @DeleteMapping("/remove/class/{classId}/subject/{subjectId}")
    public ResponseEntity<String> remove(@PathVariable Long classId,
                                         @PathVariable Long subjectId){
        classSubjectService.removeSubjectFromClass(classId, subjectId);
        return ResponseEntity.ok("Subject removed from class successfully");
    }
}