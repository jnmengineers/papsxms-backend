package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.ReportCard;
import jnm.engineer.demo.services.ReportCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reportCards")
public class ReportCardController {
    private final ReportCardService reportCardService;

    @GetMapping
    public ResponseEntity<List<ReportCard>> getAll(){
        return ResponseEntity.ok(reportCardService.getAllReportCards());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportCard> getById(@PathVariable Long id){
        return ResponseEntity.ok(reportCardService.getById(id));
    }

    // ✅ Fixed — changed @RequestParam to @PathVariable
    @GetMapping("/by-student/{studentId}")
    public ResponseEntity<List<ReportCard>> getByStudent(@PathVariable Long studentId){
        return ResponseEntity.ok(reportCardService.getByStudent(studentId));
    }

    // ✅ Fixed — changed @RequestParam to @PathVariable
    @GetMapping("/by-exam/{examId}")
    public ResponseEntity<List<ReportCard>> getByExam(@PathVariable Long examId){
        return ResponseEntity.ok(reportCardService.getByExam(examId));
    }

    @GetMapping("/student/{studentId}/exam/{examId}")
    public ResponseEntity<ReportCard> getByStudentAndExam(@PathVariable Long studentId,
                                                          @PathVariable Long examId){
        return ResponseEntity.ok(reportCardService.getByStudentAndExam(studentId, examId));
    }

    @PostMapping()
    public ResponseEntity<ReportCard> create(@RequestBody ReportCard reportCard){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportCardService.create(reportCard));
    }

    @PostMapping("/generate/student/{studentId}/exam/{examId}")
    public ResponseEntity<ReportCard> generate(@PathVariable Long studentId,
                                               @PathVariable Long examId){
        return ResponseEntity.ok(reportCardService.generateFromResults(studentId, examId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReportCard> update(@PathVariable Long id,
                                             @RequestBody ReportCard reportCard){
        return ResponseEntity.ok(reportCardService.update(id, reportCard));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        reportCardService.delete(id);
        return ResponseEntity.ok("Report card deleted successfully");
    }
}