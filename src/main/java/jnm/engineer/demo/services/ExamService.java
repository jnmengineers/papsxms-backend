package jnm.engineer.demo.services;

import jnm.engineer.demo.models.Exam;
import jnm.engineer.demo.repositories.ExamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamService {
    private final ExamRepository examRepository;

    public List<Exam> getAllExams(){
        return examRepository.findAll();
    }

    public Exam getById(Long id){
        return examRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("Exam not found"));
    }

    public List<Exam> getById(String academicYear){
        return examRepository.findByAcademicYear(academicYear);
    }

    public List<Exam> getByTerm(Integer term){
        return examRepository.findByTerm(term);
    }

    public List<Exam> getByAcademicYear(String academicYear){
        return examRepository.findByAcademicYear(academicYear);
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
        existing.setClassLevel(updated.getClassLevel());
        existing.setEndDate(updated.getEndDate());
        existing.setStartDate(updated.getStartDate());
        existing.setAcademicYearRef(updated.getAcademicYearRef());
        return examRepository.save(existing);
    }

    public void delete(Long id){
        getById(id);
         examRepository.deleteById(id);
    }
}
