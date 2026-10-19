package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.ParentFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ParentFeedbackRepository extends JpaRepository<ParentFeedback, Long> {
    List<ParentFeedback> findAllByOrderByCreatedAtDesc();
    long countByPhoneAndCreatedAtAfter(String phone, LocalDateTime after);
}
