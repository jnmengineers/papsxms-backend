package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.GradingScale;
import jnm.engineer.demo.models.ReportCard;
import jnm.engineer.demo.services.GradeScaleServices;
import jnm.engineer.demo.services.ReportCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/grading-scales")
public class GradingScaleController {
    private final GradeScaleServices gradeScaleServices;

    @GetMapping
    public ResponseEntity<List<GradingScale>> getAll(){
        return ResponseEntity.ok(gradeScaleServices.getAllGradeScales());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GradingScale> getById(@PathVariable Long id){
        return ResponseEntity.ok(gradeScaleServices.getById(id));
    }

    //Grading scale for a particular mark
    @GetMapping("/for-mark")
    public ResponseEntity<GradingScale> getById(@RequestParam Double mark){
        return ResponseEntity.ok(gradeScaleServices.getGradeForMark(mark));
    }



    // POST /api/users
    @PostMapping
    public ResponseEntity<GradingScale> create(@RequestBody GradingScale gradingScale){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gradeScaleServices.create(gradingScale));
    }



    @PutMapping("/{id}")
    public ResponseEntity<GradingScale> update(@PathVariable Long id,
                                       @RequestBody GradingScale gradingScale){
        return ResponseEntity.ok(gradeScaleServices.update(id, gradingScale));
    }



    //DELETE /api/users/1
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        gradeScaleServices.delete(id);
        return ResponseEntity.ok("Grade deleted successfully");
    }
}
