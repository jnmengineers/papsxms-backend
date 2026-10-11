package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.StaffQualification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffQualificationRepository extends JpaRepository<StaffQualification, Long> {
    List<StaffQualification> findByStaffStaffIdOrderByYearCompletedDesc(Long staffId);
}
