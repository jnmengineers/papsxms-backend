package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {
    List<AcademicYear> findByIsActiveTrue();

    List<AcademicYear> findByYearLabel(String yearLabel);
}
