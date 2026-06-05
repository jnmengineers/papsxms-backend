package jnm.engineer.demo.controllers;

import jakarta.validation.Valid;
import jnm.engineer.demo.models.ExamSchedule;
import jnm.engineer.demo.models.GradingScale;
import jnm.engineer.demo.services.ExamScheduleService;
import jnm.engineer.demo.services.GradeScaleServices;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exam-schedules")
public class ExamSheduleController {
    private final ExamScheduleService examScheduleService;

    @GetMapping
    public ResponseEntity<List<ExamSchedule>> getAll(){
        return ResponseEntity.ok(examScheduleService.getAllSchedules());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamSchedule> getById(@PathVariable Long id){
        return ResponseEntity.ok(examScheduleService.getById(id));
    }

    //schedule by exam
    @GetMapping("/by-exam/{examId}")
    public ResponseEntity<List<ExamSchedule>> getByExam(@PathVariable Long examId){
        return ResponseEntity.ok(examScheduleService.getByExam(examId));
    }

    @GetMapping("/by-class/{classId}")
    public ResponseEntity<List<ExamSchedule>> getByClass(@PathVariable Long classId){
        return ResponseEntity.ok(examScheduleService.getByClass(classId));
    }

    @GetMapping("/by-exam/{examId}/class/{classId}")
    public ResponseEntity<List<ExamSchedule>> getByExamAndClass(@PathVariable Long examId,
                                                                @PathVariable Long classId){
        return ResponseEntity.ok(examScheduleService.getByClassAndExam(examId, classId));
    }

    // POST /api/users
    @PostMapping
    public ResponseEntity<ExamSchedule> create(@Valid @RequestBody ExamSchedule examSchedule){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(examScheduleService.create(examSchedule));
    }



    @PutMapping("/{id}")
    public ResponseEntity<ExamSchedule> update(@PathVariable Long id,
                                       @Valid @RequestBody ExamSchedule examSchedule){
        return ResponseEntity.ok(examScheduleService.update(id, examSchedule));
    }



    //DELETE /api/users/1
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        examScheduleService.delete(id);
        return ResponseEntity.ok("Grade deleted successfully");
    }
}
