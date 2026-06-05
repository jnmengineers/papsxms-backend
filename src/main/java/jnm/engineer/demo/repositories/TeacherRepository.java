package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    Optional<Teacher> findByEmail(String email);

    List<Teacher> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(String name, String name1);

    boolean existsByEmail(String email);
}
