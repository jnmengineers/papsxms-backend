package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.ExamSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long> {
    List<ExamSchedule> findByExamExamId(Long examId);
    List<ExamSchedule> findBySchoolClassClassId(Long classId);
    List<ExamSchedule> findByExamExamIdAndSchoolClassClassId(Long examId, Long classId);

    List<ExamSchedule> findBySchoolClassClassIdAndExamExamId(Long classId, Long examId);
}