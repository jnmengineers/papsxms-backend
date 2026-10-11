package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.DutyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DutyAssignmentRepository extends JpaRepository<DutyAssignment, Long> {
    List<DutyAssignment> findByWeekStartBetweenOrderByWeekStartAsc(LocalDate from, LocalDate to);
    List<DutyAssignment> findByDutyCodeAndWeekStartBeforeOrderByWeekStartDesc(String dutyCode, LocalDate before);
    List<DutyAssignment> findByWeekStart(LocalDate weekStart);
}
