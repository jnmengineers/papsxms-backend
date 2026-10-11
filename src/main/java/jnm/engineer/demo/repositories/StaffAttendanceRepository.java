package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.StaffAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StaffAttendanceRepository extends JpaRepository<StaffAttendance, Long> {
    List<StaffAttendance> findByDay(LocalDate day);
    List<StaffAttendance> findByDayBetween(LocalDate from, LocalDate to);
    Optional<StaffAttendance> findByStaffStaffIdAndDay(Long staffId, LocalDate day);
}
