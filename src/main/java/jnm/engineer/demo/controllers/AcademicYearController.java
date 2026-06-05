package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.AcademicYear;
import jnm.engineer.demo.models.Exam;
import jnm.engineer.demo.services.AcademicYearService;
import jnm.engineer.demo.services.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/academic-years")
public class AcademicYearController {
    private final AcademicYearService academicYearService;

    @GetMapping
    public ResponseEntity<List<AcademicYear>> getAll(){
        return ResponseEntity.ok(academicYearService.getAllAcademicYears());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AcademicYear> getById(@PathVariable Long id){
        return ResponseEntity.ok(academicYearService.getById(id));
    }

    //schedule by exam
    @GetMapping("/active")
    public ResponseEntity<List<AcademicYear>> getActive(){
        return ResponseEntity.ok(academicYearService.getActiveYears());
    }

    @GetMapping("/label/{yearLabel}")
    public ResponseEntity<List<AcademicYear>> getByTerm(@PathVariable String yearLabel){
        return ResponseEntity.ok(academicYearService.getByYearLabel(yearLabel));
    }



    // POST /api/users
    @PostMapping
    public ResponseEntity<AcademicYear> create(@RequestBody AcademicYear academicYear){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(academicYearService.create(academicYear));
    }



    @PutMapping("/{id}")
    public ResponseEntity<AcademicYear> update(@PathVariable Long id,
                                       @RequestBody AcademicYear academicYear){
        return ResponseEntity.ok(academicYearService.update(id, academicYear));
    }

    @PatchMapping("/{id}/{set-active}")
    public ResponseEntity<AcademicYear> setActive(@PathVariable Long id){
        return ResponseEntity.ok(academicYearService.setActive(id));
    }

    //DELETE /api/users/1
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        academicYearService.delete(id);
        return ResponseEntity.ok("Grade deleted successfully");
    }
}
