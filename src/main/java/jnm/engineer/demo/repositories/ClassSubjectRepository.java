package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.ClassSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClassSubjectRepository extends JpaRepository<ClassSubject, Long> {
    List<ClassSubject> findBySchoolClassClassId(Long classId);
    List<ClassSubject> findBySubjectSubjectId(Long subjectId);
    boolean existsBySchoolClassClassIdAndSubjectSubjectId(Long classId, Long subjectId);
    void deleteBySchoolClassClassIdAndSubjectSubjectId(Long classId, Long subjectId);
}