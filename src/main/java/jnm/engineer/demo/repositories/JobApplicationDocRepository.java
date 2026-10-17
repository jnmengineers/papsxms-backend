package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.JobApplicationDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface JobApplicationDocRepository extends JpaRepository<JobApplicationDoc, Long> {
    List<JobApplicationDoc> findByApplicationIdOrderByDocIdAsc(Long applicationId);
    @Transactional
    void deleteByApplicationId(Long applicationId);
}
