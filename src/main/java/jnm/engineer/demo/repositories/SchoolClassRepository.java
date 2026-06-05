package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {
    List<SchoolClass> findByClassName(String className);

    List<SchoolClass> findByClassTeacher(Long teacherId);

    List<SchoolClass> findByClassTeacherTeacherId(Long id);
}
