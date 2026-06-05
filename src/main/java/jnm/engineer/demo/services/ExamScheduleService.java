package jnm.engineer.demo.services;

import jnm.engineer.demo.models.ExamSchedule;
import jnm.engineer.demo.repositories.ExamScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamScheduleService {
    private final ExamScheduleRepository examScheduleRepository;

    public List<ExamSchedule> getAllSchedules(){
        return examScheduleRepository.findAll();
    }

    public ExamSchedule getById(Long id){
        return examScheduleRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("schedule does not exist"));
    }

    public List<ExamSchedule> getByExam(Long examId){
        return examScheduleRepository.findByExamExamId(examId);
    }

    public List<ExamSchedule> getByClass(Long classId){
        return examScheduleRepository.findBySchoolClassClassId(classId);
    }

    public List<ExamSchedule> getByClassAndExam(Long classId, Long examId){
        return examScheduleRepository.findBySchoolClassClassIdAndExamExamId(classId, examId);
    }

    public ExamSchedule create(ExamSchedule examSchedule){
        return examScheduleRepository.save(examSchedule);
    }

    public ExamSchedule update(Long id, ExamSchedule updated){
        ExamSchedule existing = getById(id);
        existing.setExam(updated.getExam());
        existing.setExamDate(updated.getExamDate());
        existing.setEndTime(updated.getEndTime());
        existing.setSubject(updated.getSubject());
        existing.setSchoolClass(updated.getSchoolClass());
        existing.setStartTime(updated.getStartTime());
        existing.setVenue(updated.getVenue());
        return examScheduleRepository.save(existing);
    }

    public void delete(Long id){
        getById(id);
        examScheduleRepository.deleteById(id);
    }
}
