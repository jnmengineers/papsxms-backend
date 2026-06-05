package jnm.engineer.demo.controllers;

import jakarta.validation.Valid;
import jnm.engineer.demo.models.Teacher;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.services.TeacherServices;
import jnm.engineer.demo.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teachers")
public class TeacherController {
    private final TeacherServices teacherServices;

    @GetMapping
    public ResponseEntity<List<Teacher>> getAll(){
        return ResponseEntity.ok(teacherServices.getAllTeachers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Teacher> getById(@PathVariable Long id){
        return ResponseEntity.ok(teacherServices.getById(id));
    }

    //searching the teacher by name
    @GetMapping("/search")
    public ResponseEntity<List<Teacher>> search(@RequestParam String name){
        return ResponseEntity.ok(teacherServices.searchByName(name));
    }


    // POST /api/users
    @PostMapping()
    public ResponseEntity<Teacher> create(@Valid @RequestBody Teacher teacher){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teacherServices.create(teacher));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Teacher> update(@PathVariable Long id,
                                       @Valid @RequestBody Teacher teacher){
        return ResponseEntity.ok(teacherServices.update(id, teacher));
    }


    //DELETE /api/users/1
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        teacherServices.delete(id);
        return ResponseEntity.ok("user deleted successfully");
    }
}
