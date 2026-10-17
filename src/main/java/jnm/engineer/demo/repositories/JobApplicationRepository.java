package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findAllByOrderByCreatedAtDesc();
    long countByPhoneAndCreatedAtAfter(String phone, LocalDateTime after);
}
