package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.FeeReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FeeReminderLogRepository extends JpaRepository<FeeReminderLog, Long> {
    List<FeeReminderLog> findBySentOnGreaterThanEqual(LocalDate from);
}
