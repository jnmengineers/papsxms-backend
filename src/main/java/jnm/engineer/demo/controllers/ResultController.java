package jnm.engineer.demo.controllers;

import jnm.engineer.demo.dto.BulkResultRequest;
import jnm.engineer.demo.dto.BulkResultResponse;
import jnm.engineer.demo.models.Result;
import jnm.engineer.demo.services.ResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/by-student/{studentId}")
    public ResponseEntity<List<Result>> getByStudent(@PathVariable Long studentId){
        return ResponseEntity.ok(resultService.getByStudent(studentId));
    }

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
                                         @RequestBody Result result){
        return ResponseEntity.ok(resultService.update(id, result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        resultService.delete(id);
        return ResponseEntity.ok("Result deleted successfully");
    }

    // ✅ Bulk save all marks in one request
    @PostMapping("/bulk-save")
    public ResponseEntity<BulkResultResponse> bulkSave(@RequestBody BulkResultRequest request){
        return ResponseEntity.ok(resultService.bulkSave(request));
    }

    // ✅ NEW — Progressive results for a student across all exams in a term
    // Returns results grouped by subject with marks per exam type (OPENING/MID_TERM/END_TERM)
    @GetMapping("/progressive/student/{studentId}/term/{term}/year/{academicYear}")
    public ResponseEntity<Map<String, Object>> getProgressiveResults(
            @PathVariable Long studentId,
            @PathVariable Integer term,
            @PathVariable String academicYear) {
        return ResponseEntity.ok(resultService.getProgressiveResults(studentId, term, academicYear));
    }

    // ✅ NEW — Most improved students in a class for a term
    // Compares average from OPENING to latest exam available
    @GetMapping("/progressive/class/{className}/term/{term}/year/{academicYear}/improvements")
    public ResponseEntity<List<Map<String, Object>>> getMostImproved(
            @PathVariable String className,
            @PathVariable Integer term,
            @PathVariable String academicYear) {
        return ResponseEntity.ok(resultService.getMostImprovedStudents(className, term, academicYear));
    }
}