package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.LeaveHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LeaveHolidayRepository extends JpaRepository<LeaveHoliday, LocalDate> {
    List<LeaveHoliday> findAllByOrderByHolidayDateAsc();
}
