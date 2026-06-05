package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Student;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.services.SchoolClassService;
import jnm.engineer.demo.services.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/classes")
public class SchoolClassController {
    private final SchoolClassService schoolClassService;

    @GetMapping
    public ResponseEntity<List<SchoolClass>> getAll(){
        return ResponseEntity.ok(schoolClassService.getAllSchoolClasses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SchoolClass> getById(@PathVariable Long id){
        return ResponseEntity.ok(schoolClassService.getById(id));
    }

    //searching the teacher by name
    @GetMapping("/by-name")
    public ResponseEntity<List<SchoolClass>> getBYClassName(@RequestParam String className){
        return ResponseEntity.ok(schoolClassService.getBySchoolClassName(className));
    }

    @GetMapping("/by-teacher/{teacherId}")
    public ResponseEntity<List<SchoolClass>> getByTeacher(@RequestParam Long teacherId){
        return ResponseEntity.ok(schoolClassService.getByClassTeacher(teacherId));
    }


    // POST /api/users
    @PostMapping()
    public ResponseEntity<SchoolClass> create(@RequestBody SchoolClass schoolClass){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(schoolClassService.create(schoolClass));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SchoolClass> update(@PathVariable Long id,
                                       @RequestBody SchoolClass schoolClass){
        return ResponseEntity.ok(schoolClassService.update(id, schoolClass));
    }

    @PatchMapping ("/{classId}/assign-teacher/{teacherId}")
    public ResponseEntity<SchoolClass> assignTeacher(@PathVariable Long classId,
                                                     @PathVariable Long teacherId){
        return ResponseEntity.ok(schoolClassService.assignClassTeacher(classId, teacherId));
    }


    //DELETE /api/users/1
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        schoolClassService.delete(id);
        return ResponseEntity.ok("user deleted successfully");
    }
}
