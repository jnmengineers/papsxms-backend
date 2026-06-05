package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByAcademicYear(String academicYear);

    List<Exam> findByTerm(Integer term);

    List<Exam> findByClassLevel(String classLevel);
}
