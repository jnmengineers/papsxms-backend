package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.Exam;
import jnm.engineer.demo.models.ExamSchedule;
import jnm.engineer.demo.services.ExamScheduleService;
import jnm.engineer.demo.services.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exams")
public class ExamController {
    private final ExamService examService;

    @GetMapping
    public ResponseEntity<List<Exam>> getAll(){
        return ResponseEntity.ok(examService.getAllExams());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Exam> getById(@PathVariable Long id){
        return ResponseEntity.ok(examService.getById(id));
    }

    //schedule by exam
    @GetMapping("/by-year")
    public ResponseEntity<List<Exam>> getByExam(@RequestParam String academicYear){
        return ResponseEntity.ok(examService.getByAcademicYear(academicYear));
    }

    @GetMapping("/by-term")
    public ResponseEntity<List<Exam>> getByTerm(@RequestParam Integer term){
        return ResponseEntity.ok(examService.getByTerm(term));
    }

    @GetMapping("/by-class")
    public ResponseEntity<List<Exam>> getByClass(@RequestParam String classLevel){
        return ResponseEntity.ok(examService.getByClassLevel(classLevel));
    }

    // POST /api/users
    @PostMapping
    public ResponseEntity<Exam> create(@RequestBody Exam exam){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(examService.create(exam));
    }



    @PutMapping("/{id}")
    public ResponseEntity<Exam> update(@PathVariable Long id,
                                       @RequestBody Exam exam){
        return ResponseEntity.ok(examService.update(id, exam));
    }



    //DELETE /api/users/1
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        examService.delete(id);
        return ResponseEntity.ok("Grade deleted successfully");
    }
}
