package jnm.engineer.demo.services;

import jnm.engineer.demo.dto.BulkResultRequest;
import jnm.engineer.demo.dto.BulkResultResponse;
import jnm.engineer.demo.models.Exam;
import jnm.engineer.demo.models.Result;
import jnm.engineer.demo.models.Student;
import jnm.engineer.demo.models.Subject;
import jnm.engineer.demo.repositories.ExamRepository;
import jnm.engineer.demo.repositories.GradeScaleRepository;
import jnm.engineer.demo.repositories.ResultRepository;
import jnm.engineer.demo.repositories.StudentRepository;
import jnm.engineer.demo.repositories.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.List;


@Service
@RequiredArgsConstructor
public class ResultService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ResultService.class);
    private final ResultRepository resultRepository;
    private final GradeScaleRepository gradeScaleRepository;
    private final StudentRepository studentRepository;
    private final ExamRepository examRepository;
    private final SubjectRepository subjectRepository;

    public List<Result> getAllResults(){
        return resultRepository.findAll();
    }

    public Result getById(Long id){
        return resultRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("Result not found"));
    }

    public List<Result> getByStudent(Long studentId){
        return resultRepository.findByStudentStudentId(studentId);
    }

    public List<Result> getByExam(Long examId){
        return resultRepository.findByExamExamId(examId);
    }

    public List<Result> getByStudentAndExam(Long studentId, Long examId){
        return resultRepository.findByStudentStudentIdAndExamExamId(studentId, examId);
    }

    public Result create(Result result){
        Student student = studentRepository.findById(result.getStudent().getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found"));
        Exam exam = examRepository.findById(result.getExam().getExamId())
                .orElseThrow(() -> new RuntimeException("Exam not found"));
        Subject subject = subjectRepository.findById(result.getSubject().getSubjectId())
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        result.setStudent(student);
        result.setExam(exam);
        result.setSubject(subject);

        boolean exists = resultRepository.existsByStudentStudentIdAndExamExamIdAndSubjectSubjectId(
                student.getStudentId(), exam.getExamId(), subject.getSubjectId()
        );
        if (exists){
            throw new RuntimeException("Result already exists for this student");
        }

        if (result.getGrade() == null){
            gradeScaleRepository.findByMark(result.getMarksObtained()).ifPresent(scale -> {
                result.setGrade(scale.getGradeLetter());
                result.setRemarks(scale.getRemarks());
            });
        }

        return resultRepository.save(result);
    }

    public Result update(Long id, Result updated){
        Result existing = getById(id);
        existing.setMarksObtained(updated.getMarksObtained());
        existing.setMaxMarks(updated.getMaxMarks());

        gradeScaleRepository.findByMark(updated.getMarksObtained()).ifPresent(scale -> {
            existing.setGrade(scale.getGradeLetter());
            existing.setRemarks(scale.getRemarks());
        });
        return resultRepository.save(existing);
    }

    public void delete(Long id){
        getById(id);
        resultRepository.deleteById(id);
    }

    // ✅ Bulk save — one transaction for all marks
    // Skips empty marks, handles duplicates, auto-calculates grades
    @Transactional
    public BulkResultResponse bulkSave(BulkResultRequest request) {
        int saved = 0, updated = 0, skipped = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        // Resolve exam once
        Exam exam = examRepository.findById(request.getExamId())
                .orElseThrow(() -> new RuntimeException("Exam not found: " + request.getExamId()));

        for (BulkResultRequest.ResultItem item : request.getResults()) {

            // Skip empty or invalid marks silently
            if (item.getMarksObtained() == null) { skipped++; continue; }
            if (item.getMarksObtained() < 0 || item.getMarksObtained() > 100) { skipped++; continue; }

            try {
                // ── ALWAYS UPSERT: check if result exists by student+exam+subject ──
                // This handles all cases: new, update, retry after partial failure
                Optional<Result> existingOpt = resultRepository
                        .findByStudentStudentIdAndExamExamIdAndSubjectSubjectId(
                                item.getStudentId(), request.getExamId(), item.getSubjectId()
                        );

                if (existingOpt.isPresent()) {
                    // ── UPDATE existing ──────────────────────────────────────
                    Result existing = existingOpt.get();
                    existing.setMarksObtained(item.getMarksObtained());
                    existing.setMaxMarks(item.getMaxMarks() != null ? item.getMaxMarks() : 100.0);
                    gradeScaleRepository.findByMark(item.getMarksObtained()).ifPresent(scale -> {
                        existing.setGrade(scale.getGradeLetter());
                        existing.setRemarks(scale.getRemarks());
                    });
                    resultRepository.save(existing);
                    updated++;
                } else {
                    // ── CREATE new ───────────────────────────────────────────
                    saved += createNewResult(exam, item, errors);
                }

            } catch (Exception e) {
                failed++;
                String errMsg = "Student " + item.getStudentId() + " / Subject " + item.getSubjectId() + ": " + e.getMessage();
                errors.add(errMsg);
                log.error("BulkSave error: {}", errMsg, e);
            }
        }

        log.info("BulkSave complete — saved:{} updated:{} skipped:{} failed:{}", saved, updated, skipped, failed);
        String message = String.format("Saved: %d, Updated: %d, Skipped: %d, Failed: %d",
                saved, updated, skipped, failed);
        return new BulkResultResponse(saved, updated, skipped, failed, errors, message);
    }

    // Helper — creates a new Result record
    private int createNewResult(Exam exam, BulkResultRequest.ResultItem item, List<String> errors) {
        try {
            Student student = studentRepository.findById(item.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Student not found: " + item.getStudentId()));
            Subject subject = subjectRepository.findById(item.getSubjectId())
                    .orElseThrow(() -> new RuntimeException("Subject not found: " + item.getSubjectId()));

            Result result = new Result();
            result.setStudent(student);
            result.setSubject(subject);
            result.setExam(exam);
            result.setMarksObtained(item.getMarksObtained());
            result.setMaxMarks(item.getMaxMarks() != null ? item.getMaxMarks() : 100.0);

            // Auto-calculate grade from gradeScale table
            gradeScaleRepository.findByMark(item.getMarksObtained()).ifPresent(scale -> {
                result.setGrade(scale.getGradeLetter());
                result.setRemarks(scale.getRemarks());
            });

            resultRepository.save(result);
            return 1;
        } catch (Exception e) {
            errors.add("Create failed - Student " + item.getStudentId() + ": " + e.getMessage());
            return 0;
        }
    }

    // ── Progressive results for a student across all exams in a term ──────────
    public Map<String, Object> getProgressiveResults(Long studentId, Integer term, String academicYear) {

        // Get all exams for this term and year, ordered by exam type
        List<Exam> termExams = examRepository.findAll().stream()
                .filter(e -> e.getTerm() != null && e.getTerm().equals(term)
                        && academicYear.equals(e.getAcademicYear()))
                .sorted((a, b) -> {
                    int orderA = examTypeOrder(a.getExamType());
                    int orderB = examTypeOrder(b.getExamType());
                    return Integer.compare(orderA, orderB);
                })
                .collect(java.util.stream.Collectors.toList());

        // Get all results for this student in this term
        List<Result> allResults = resultRepository.findByStudentStudentId(studentId).stream()
                .filter(r -> termExams.stream()
                        .anyMatch(e -> e.getExamId().equals(r.getExam().getExamId())))
                .collect(java.util.stream.Collectors.toList());

        // Group by subject
        Map<String, Map<String, Object>> subjectMap = new java.util.LinkedHashMap<>();
        for (Result r : allResults) {
            String subjectName = r.getSubject().getSubjectName();
            String subjectId = r.getSubject().getSubjectId().toString();
            String key = subjectId + "_" + subjectName;

            if (!subjectMap.containsKey(key)) {
                Map<String, Object> subjectData = new java.util.LinkedHashMap<>();
                subjectData.put("subjectId", subjectId);
                subjectData.put("subjectName", subjectName);
                subjectData.put("marks", new java.util.LinkedHashMap<String, Object>());
                subjectMap.put(key, subjectData);
            }

            String examType = r.getExam().getExamType() != null ? r.getExam().getExamType() : "MID_TERM";
            @SuppressWarnings("unchecked")
            Map<String, Object> marks = (Map<String, Object>) subjectMap.get(key).get("marks");
            marks.put(examType, r.getMarksObtained());
        }

        // Calculate trend per subject (first exam → last exam available)
        List<Map<String, Object>> subjects = new java.util.ArrayList<>();
        for (Map<String, Object> subData : subjectMap.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> marks = (Map<String, Object>) subData.get("marks");

            Double opening = marks.containsKey("OPENING") ? ((Number) marks.get("OPENING")).doubleValue() : null;
            Double midTerm = marks.containsKey("MID_TERM") ? ((Number) marks.get("MID_TERM")).doubleValue() : null;
            Double endTerm = marks.containsKey("END_TERM") ? ((Number) marks.get("END_TERM")).doubleValue() : null;

            // Latest available minus first available
            Double first = opening != null ? opening : midTerm;
            Double latest = endTerm != null ? endTerm : (midTerm != null ? midTerm : opening);
            Double change = (first != null && latest != null && !first.equals(latest)) ? latest - first : null;
            String trend = change == null ? "—" : change > 0 ? "↑" : change < 0 ? "↓" : "↔";
            String trendColor = change == null ? "#999" : change > 0 ? "#28a745" : change < 0 ? "#dc3545" : "#ffc107";

            subData.put("opening", opening);
            subData.put("midTerm", midTerm);
            subData.put("endTerm", endTerm);
            subData.put("change", change != null ? String.format("%+.1f", change) : "—");
            subData.put("trend", trend);
            subData.put("trendColor", trendColor);
            subjects.add(subData);
        }

        // Student info
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("student", student);
        response.put("term", term);
        response.put("academicYear", academicYear);
        response.put("exams", termExams);
        response.put("subjects", subjects);
        return response;
    }

    // ── Most improved students in a class for a term ──────────────────────────
    public List<Map<String, Object>> getMostImprovedStudents(String className, Integer term, String academicYear) {

        // Get opening and latest exam for this term
        List<Exam> termExams = examRepository.findAll().stream()
                .filter(e -> e.getTerm() != null && e.getTerm().equals(term)
                        && academicYear.equals(e.getAcademicYear()))
                .sorted((a, b) -> Integer.compare(examTypeOrder(a.getExamType()), examTypeOrder(b.getExamType())))
                .collect(java.util.stream.Collectors.toList());

        if (termExams.size() < 2) return new java.util.ArrayList<>();

        Exam firstExam = termExams.get(0);
        Exam latestExam = termExams.get(termExams.size() - 1);

        // Get results for both exams filtered by class
        List<Result> firstResults = resultRepository.findByExamExamId(firstExam.getExamId()).stream()
                .filter(r -> className.equals(r.getStudent().getClassName()))
                .collect(java.util.stream.Collectors.toList());

        List<Result> latestResults = resultRepository.findByExamExamId(latestExam.getExamId()).stream()
                .filter(r -> className.equals(r.getStudent().getClassName()))
                .collect(java.util.stream.Collectors.toList());

        // Calculate average per student in each exam
        Map<Long, Double> firstAvg = averageByStudent(firstResults);
        Map<Long, Double> latestAvg = averageByStudent(latestResults);

        // Calculate improvement
        List<Map<String, Object>> improvements = new java.util.ArrayList<>();
        for (Long studentId : latestAvg.keySet()) {
            if (!firstAvg.containsKey(studentId)) continue;
            double improvement = latestAvg.get(studentId) - firstAvg.get(studentId);

            // Get student name
            Result sample = latestResults.stream()
                    .filter(r -> r.getStudent().getStudentId().equals(studentId))
                    .findFirst().orElse(null);
            if (sample == null) continue;

            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("studentId", studentId);
            entry.put("studentName", sample.getStudent().getFirstName() + " " + sample.getStudent().getLastName());
            entry.put("admissionNumber", sample.getStudent().getAdmissionNumber());
            entry.put("className", className);
            entry.put("openingAvg", String.format("%.1f", firstAvg.get(studentId)));
            entry.put("latestAvg", String.format("%.1f", latestAvg.get(studentId)));
            entry.put("improvement", String.format("%+.1f", improvement));
            entry.put("improvementValue", improvement);
            entry.put("trend", improvement > 0 ? "↑" : improvement < 0 ? "↓" : "↔");
            entry.put("trendColor", improvement > 0 ? "#28a745" : improvement < 0 ? "#dc3545" : "#ffc107");
            improvements.add(entry);
        }

        // Sort by improvement descending
        improvements.sort((a, b) -> Double.compare(
                (Double) b.get("improvementValue"),
                (Double) a.get("improvementValue")
        ));

        return improvements;
    }

    // Helper — order OPENING=1, MID_TERM=2, END_TERM=3
    private int examTypeOrder(String examType) {
        if (examType == null) return 2;
        switch (examType) {
            case "OPENING": return 1;
            case "MID_TERM": return 2;
            case "END_TERM": return 3;
            default: return 2;
        }
    }

    // Helper — average marks per student from a list of results
    private Map<Long, Double> averageByStudent(List<Result> results) {
        Map<Long, List<Double>> grouped = new java.util.LinkedHashMap<>();
        for (Result r : results) {
            Long sid = r.getStudent().getStudentId();
            grouped.computeIfAbsent(sid, k -> new java.util.ArrayList<>()).add(r.getMarksObtained());
        }
        Map<Long, Double> averages = new java.util.LinkedHashMap<>();
        grouped.forEach((sid, marks) ->
                averages.put(sid, marks.stream().mapToDouble(Double::doubleValue).average().orElse(0))
        );
        return averages;
    }
}