package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findByGradeLevel(String gradeLevel);

    List<Subject> findByTeacherTeacherId(Long teacherId);

    boolean existsBySubjectCode(String subjectCode);
}
