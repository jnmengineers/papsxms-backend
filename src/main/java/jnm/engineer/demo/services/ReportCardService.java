package jnm.engineer.demo.services;

import jnm.engineer.demo.models.ReportCard;
import jnm.engineer.demo.models.Result;
import jnm.engineer.demo.models.Student;
import jnm.engineer.demo.models.Exam;
import jnm.engineer.demo.repositories.ReportCardRepository;
import jnm.engineer.demo.repositories.ResultRepository;
import jnm.engineer.demo.repositories.StudentRepository;
import jnm.engineer.demo.repositories.ExamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportCardService {

    private final ReportCardRepository reportCardRepository;
    private final ResultRepository resultRepository;
    private final StudentRepository studentRepository;
    private final ExamRepository examRepository;
    private final RankingService rankingService;

    public List<ReportCard> getAllReportCards(){
        return reportCardRepository.findAll();
    }

    public ReportCard getById(Long id){
        return reportCardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("The report card doesn't exist"));
    }

    public List<ReportCard> getByStudent(Long studentId){
        return reportCardRepository.findByStudentStudentId(studentId);
    }

    public List<ReportCard> getByExam(Long examId){
        return reportCardRepository.findByExamExamId(examId);
    }

    public ReportCard getByStudentAndExam(Long studentId, Long examId){
        return reportCardRepository.findByStudentStudentIdAndExamExamId(studentId, examId)
                .orElseThrow(() -> new RuntimeException("The report card does not exist"));
    }

    public ReportCard create(ReportCard reportCard){
        boolean exists = reportCardRepository.existsByStudentStudentIdAndExamExamId(
                reportCard.getStudent().getStudentId(),
                reportCard.getExam().getExamId());
        if (exists){
            throw new RuntimeException("Report card already exists for this student");
        }
        return reportCardRepository.save(reportCard);
    }

    public ReportCard generateFromResults(Long studentId, Long examId) {
        // Get student and exam
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));

        // Get all results for this student and exam
        List<Result> results = resultRepository.findByStudentStudentIdAndExamExamId(studentId, examId);

        if (results.isEmpty()) {
            throw new RuntimeException("No results found for this student in this exam");
        }

        // Calculate total and average
        double totalMarks = results.stream()
                .mapToDouble(Result::getMarksObtained)
                .sum();
        double averageMarks = totalMarks / results.size();

        // Check if report card already exists — update it if so
        ReportCard reportCard = reportCardRepository
                .findByStudentStudentIdAndExamExamId(studentId, examId)
                .orElse(new ReportCard());

        reportCard.setStudent(student);
        reportCard.setExam(exam);
        reportCard.setTotalMarks(totalMarks);
        reportCard.setAverageMarks(Math.round(averageMarks * 100.0) / 100.0);

        // Save report card
        ReportCard saved = reportCardRepository.save(reportCard);

        // Auto calculate ranks after saving
        rankingService.calculateRanksForExam(examId);

        return reportCardRepository.findById(saved.getReportId()).orElse(saved);
    }

    public ReportCard update(Long id, ReportCard updated){
        ReportCard existing = getById(id);
        existing.setTermRank(updated.getTermRank());
        existing.setClassRank(updated.getClassRank());
        existing.setTotalMarks(updated.getTotalMarks());
        existing.setAverageMarks(updated.getAverageMarks());
        existing.setRemarks(updated.getRemarks());
        existing.setTeacherComment(updated.getTeacherComment());
        existing.setPrincipalComment(updated.getPrincipalComment());
        return reportCardRepository.save(existing);
    }

    public void delete(Long id){
        getById(id);
        reportCardRepository.deleteById(id);
    }
}