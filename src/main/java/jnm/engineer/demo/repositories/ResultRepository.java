package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Result;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResultRepository extends JpaRepository<Result, Long> {

    List<Result> findByStudentStudentId(Long studentId);

    List<Result> findByExamExamId(Long examId);

    List<Result> findByStudentStudentIdAndExamExamId(Long studentId, Long examId);

    boolean existsByStudentStudentIdAndExamExamIdAndSubjectSubjectId(Long studentId, Long examId, Long subjectId);
}