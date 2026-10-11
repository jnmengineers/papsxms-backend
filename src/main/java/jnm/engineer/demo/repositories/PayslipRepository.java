package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    List<Payslip> findByRunRunIdOrderByFullNameAsc(Long runId);
}
