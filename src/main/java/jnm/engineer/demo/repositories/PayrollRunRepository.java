package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {
    List<PayrollRun> findAllByOrderByPeriodDesc();
    Optional<PayrollRun> findByPeriod(String period);
}
