package jnm.engineer.demo.controllers;

import jnm.engineer.demo.services.RankingService;
import jnm.engineer.demo.services.SectionReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rankings")
public class RankingController {

    private final RankingService rankingService;
    private final SectionReportService sectionReportService;

    // Calculate and update ranks for all students in an exam
    @PostMapping("/calculate/{examId}")
    public ResponseEntity<String> calculateRanks(@PathVariable Long examId) {
        rankingService.calculateRanksForExam(examId);
        return ResponseEntity.ok("Ranks calculated successfully for exam " + examId);
    }

    // Get section report for an exam
    @GetMapping("/section-report/{examId}")
    public ResponseEntity<Map<String, Object>> getSectionReport(@PathVariable Long examId) {
        return ResponseEntity.ok(sectionReportService.getSectionReport(examId));
    }
}