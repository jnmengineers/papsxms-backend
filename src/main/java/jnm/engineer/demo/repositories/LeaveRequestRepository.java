package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByStaffStaffIdOrderByStartDateDesc(Long staffId);
    List<LeaveRequest> findByStatusOrderByStartDateAsc(String status);
    List<LeaveRequest> findByEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAsc(LocalDate from, LocalDate to);
}
