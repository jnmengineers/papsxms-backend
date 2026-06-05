package jnm.engineer.demo.controllers;

import jakarta.validation.Valid;
import jnm.engineer.demo.models.Result;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.services.ResultService;
import jnm.engineer.demo.services.SchoolClassService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/results")
public class ResultController {
    private final ResultService resultService;

    @GetMapping
    public ResponseEntity<List<Result>> getAll(){
        return ResponseEntity.ok(resultService.getAllResults());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result> getById(@PathVariable Long id){
        return ResponseEntity.ok(resultService.getById(id));
    }

    //searching the teacher by name
    @GetMapping("/by-student/{studentId}")
    public ResponseEntity<List<Result>> getBYClassName(@RequestParam Long studentId){
        return ResponseEntity.ok(resultService.getByStudent(studentId));
    }

    @GetMapping("/by-exam/{examId}")
    public ResponseEntity<List<Result>> getByExam(@RequestParam Long examId){
        return ResponseEntity.ok(resultService.getByExam(examId));
    }

    @GetMapping("/student/{studentId}/exam/{examId}")
    public ResponseEntity<List<Result>> getByStudentAndExam(@PathVariable Long studentId,
                                                            @PathVariable Long examId){
        return ResponseEntity.ok(resultService.getByStudentAndExam(studentId, examId));
    }

    // POST /api/users
    @PostMapping()
    public ResponseEntity<Result> create(@Valid @RequestBody Result result){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resultService.create(result));
    }



    @PutMapping("/{id}")
    public ResponseEntity<Result> update(@PathVariable Long id,
                                       @Valid @RequestBody Result result){
        return ResponseEntity.ok(resultService.update(id, result));
    }

    //DELETE /api/users/1
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        resultService.delete(id);
        return ResponseEntity.ok("Result deleted successfully");
    }
}
