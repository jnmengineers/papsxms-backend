package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.TeachingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, Long> {

    List<TeachingAssignment> findByTeacherTeacherId(Long teacherId);

    List<TeachingAssignment> findBySchoolClassClassId(Long classId);

    Optional<TeachingAssignment> findBySchoolClassClassIdAndSubjectSubjectId(Long classId, Long subjectId);
}
