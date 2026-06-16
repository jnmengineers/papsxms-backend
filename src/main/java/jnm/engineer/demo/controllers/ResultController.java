package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.Result;
import jnm.engineer.demo.services.ResultService;
import jakarta.validation.Valid;
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

    // ✅ Fixed — changed @RequestParam to @PathVariable
    @GetMapping("/by-student/{studentId}")
    public ResponseEntity<List<Result>> getByStudent(@PathVariable Long studentId){
        return ResponseEntity.ok(resultService.getByStudent(studentId));
    }

    // ✅ Fixed — changed @RequestParam to @PathVariable
    @GetMapping("/by-exam/{examId}")
    public ResponseEntity<List<Result>> getByExam(@PathVariable Long examId){
        return ResponseEntity.ok(resultService.getByExam(examId));
    }

    @GetMapping("/student/{studentId}/exam/{examId}")
    public ResponseEntity<List<Result>> getByStudentAndExam(@PathVariable Long studentId,
                                                            @PathVariable Long examId){
        return ResponseEntity.ok(resultService.getByStudentAndExam(studentId, examId));
    }

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

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        resultService.delete(id);
        return ResponseEntity.ok("Result deleted successfully");
    }
}