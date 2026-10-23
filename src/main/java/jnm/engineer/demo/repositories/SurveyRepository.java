package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Survey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SurveyRepository extends JpaRepository<Survey, Long> {
    Optional<Survey> findByCode(String code);
    boolean existsByCode(String code);
    List<Survey> findAllByOrderByCreatedAtDesc();
}
