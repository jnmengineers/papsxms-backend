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
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResultService {
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
                if (item.getResultId() != null) {
                    // ── UPDATE existing result ───────────────────────────────
                    Result existing = resultRepository.findById(item.getResultId())
                            .orElse(null);

                    if (existing != null) {
                        existing.setMarksObtained(item.getMarksObtained());
                        existing.setMaxMarks(item.getMaxMarks() != null ? item.getMaxMarks() : 100.0);
                        // Re-calculate grade using gradeScale
                        gradeScaleRepository.findByMark(item.getMarksObtained()).ifPresent(scale -> {
                            existing.setGrade(scale.getGradeLetter());
                            existing.setRemarks(scale.getRemarks());
                        });
                        resultRepository.save(existing);
                        updated++;
                    } else {
                        // resultId given but not found — create new
                        saved += createNewResult(exam, item, errors);
                    }

                } else {
                    // ── CHECK for existing result (avoid duplicate) ──────────
                    boolean exists = resultRepository.existsByStudentStudentIdAndExamExamIdAndSubjectSubjectId(
                            item.getStudentId(), request.getExamId(), item.getSubjectId()
                    );

                    if (exists) {
                        // Update the existing record instead of creating duplicate
                        resultRepository.findByStudentStudentIdAndExamExamIdAndSubjectSubjectId(
                                item.getStudentId(), request.getExamId(), item.getSubjectId()
                        ).ifPresent(existing -> {
                            existing.setMarksObtained(item.getMarksObtained());
                            existing.setMaxMarks(item.getMaxMarks() != null ? item.getMaxMarks() : 100.0);
                            gradeScaleRepository.findByMark(item.getMarksObtained()).ifPresent(scale -> {
                                existing.setGrade(scale.getGradeLetter());
                                existing.setRemarks(scale.getRemarks());
                            });
                            resultRepository.save(existing);
                        });
                        updated++;
                    } else {
                        saved += createNewResult(exam, item, errors);
                    }
                }

            } catch (Exception e) {
                failed++;
                errors.add("Student " + item.getStudentId() + " / Subject " + item.getSubjectId() + ": " + e.getMessage());
            }
        }

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
}