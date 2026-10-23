package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {
    List<SurveyResponse> findBySurveyIdOrderByCreatedAtDesc(Long surveyId);
    long countBySurveyId(Long surveyId);
    boolean existsBySurveyIdAndPhone(Long surveyId, String phone);
    @Transactional
    void deleteBySurveyId(Long surveyId);
}
