package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.SubjectLesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectLessonRepository extends JpaRepository<SubjectLesson, Long> {
    List<SubjectLesson> findByGradeLevel(String gradeLevel);
    Optional<SubjectLesson> findByGradeLevelAndSubjectSubjectId(String gradeLevel, Long subjectId);
}
