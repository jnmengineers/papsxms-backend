package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.ReportCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportCardRepository extends JpaRepository<ReportCard, Long> {
    List<ReportCard> findByStudentStudentId(Long studentId);
    List<ReportCard> findByExamExamId(Long examId);
    Optional<ReportCard> findByStudentStudentIdAndExamExamId(Long studentId, Long examId);
    boolean existsByStudentStudentIdAndExamExamId(Long studentId, Long examId);
}