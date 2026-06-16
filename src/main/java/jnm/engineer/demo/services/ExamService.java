package jnm.engineer.demo.services;

import jnm.engineer.demo.models.Exam;
import jnm.engineer.demo.models.ExamSchedule;
import jnm.engineer.demo.models.ReportCard;
import jnm.engineer.demo.models.Result;
import jnm.engineer.demo.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ResultRepository resultRepository;
    private final ReportCardRepository reportCardRepository;
    private final ExamScheduleRepository examScheduleRepository;

    public List<Exam> getAllExams(){
        return examRepository.findAll();
    }

    public Exam getById(Long id){
        return examRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exam not found with id: " + id));
    }

    public List<Exam> getByAcademicYear(String academicYear){
        return examRepository.findByAcademicYear(academicYear);
    }

    public List<Exam> getByTerm(Integer term){
        return examRepository.findByTerm(term);
    }

    public List<Exam> getByClassLevel(String classLevel){
        return examRepository.findByClassLevel(classLevel);
    }

    public Exam create(Exam exam){
        return examRepository.save(exam);
    }

    public Exam update(Long id, Exam updated){
        Exam existing = getById(id);
        existing.setExamName(updated.getExamName());
        existing.setAcademicYear(updated.getAcademicYear());
        existing.setTerm(updated.getTerm());
        existing.setStartDate(updated.getStartDate());
        existing.setEndDate(updated.getEndDate());
        existing.setClassLevel(updated.getClassLevel());
        return examRepository.save(existing);
    }

    @Transactional
    public void delete(Long id){
        getById(id);

        // Delete report cards first
        List<ReportCard> reportCards = reportCardRepository.findByExamExamId(id);
        reportCardRepository.deleteAll(reportCards);

        // Delete results
        List<Result> results = resultRepository.findByExamExamId(id);
        resultRepository.deleteAll(results);

        // Delete exam schedules
        List<ExamSchedule> schedules = examScheduleRepository.findByExamExamId(id);
        examScheduleRepository.deleteAll(schedules);

        examRepository.deleteById(id);
    }
}