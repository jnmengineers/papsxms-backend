package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.FeeStructure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, Long> {
    Optional<FeeStructure> findBySectionAndYearLabelAndTerm(String section, String yearLabel, Integer term);
    List<FeeStructure> findByYearLabelAndTerm(String yearLabel, Integer term);
}
