package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.SmsLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {
    List<SmsLog> findTop200BySentAtAfterOrderBySentAtDesc(LocalDateTime after);
}
