package jnm.engineer.demo.services;

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
        // Resolve entities from DB
        Student student = studentRepository.findById(result.getStudent().getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found"));
        Exam exam = examRepository.findById(result.getExam().getExamId())
                .orElseThrow(() -> new RuntimeException("Exam not found"));
        Subject subject = subjectRepository.findById(result.getSubject().getSubjectId())
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        result.setStudent(student);
        result.setExam(exam);
        result.setSubject(subject);

        // Check if result already exists
        boolean exists = resultRepository.existsByStudentStudentIdAndExamExamIdAndSubjectSubjectId(
                student.getStudentId(),
                exam.getExamId(),
                subject.getSubjectId()
        );
        if (exists){
            throw new RuntimeException("Result already exists for this student");
        }

        // Auto calculate grade if not provided
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

        // Re-calculate grade automatically
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
}