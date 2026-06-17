package jnm.engineer.demo.controllers;

import jakarta.validation.Valid;
import jnm.engineer.demo.models.Student;
import jnm.engineer.demo.models.Teacher;
import jnm.engineer.demo.repositories.StudentRepository;
import jnm.engineer.demo.services.StudentService;
import jnm.engineer.demo.services.TeacherServices;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/students")
public class StudentController {
    private final StudentService studentService;

    @GetMapping
    public ResponseEntity<List<Student>> getAll(){
        return ResponseEntity.ok(studentService.getAllStudents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Student> getById(@PathVariable Long id){
        return ResponseEntity.ok(studentService.getById(id));
    }

    //searching the teacher by name
    @GetMapping("/by-class-name")
    public ResponseEntity<List<Student>> getBYClassName(@RequestParam String className){
        return ResponseEntity.ok(studentService.searchByName(className));
    }

    @GetMapping("/by-class/{classId}")
    public ResponseEntity<List<Student>> getBYClass(@RequestParam Long classId){
        return ResponseEntity.ok(studentService.GetByClass(classId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Student>> search(@RequestParam String name){
        return ResponseEntity.ok(studentService.searchByName(name));
    }


    // POST /api/users
    @PostMapping()
    public ResponseEntity<Student> create(@Valid @RequestBody Student student,
                                          @RequestParam(required = false) Long classId){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(studentService.create(student, classId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Student> update(@PathVariable Long id,
                                          @Valid @RequestBody Student student){
        return ResponseEntity.ok(studentService.update(id, student));
    }


    //DELETE /api/users/1
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        studentService.delete(id);
        return ResponseEntity.ok("user deleted successfully");
    }


    @PutMapping("/{studentId}/move-class/{classId}")
    public ResponseEntity<Student> moveToClass(
            @PathVariable Long studentId,
            @PathVariable Long classId) {
        return ResponseEntity.ok(studentService.moveToClass(studentId, classId));
    }
}
