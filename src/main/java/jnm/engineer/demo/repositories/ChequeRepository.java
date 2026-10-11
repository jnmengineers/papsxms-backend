package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Cheque;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ChequeRepository extends JpaRepository<Cheque, Long> {
    boolean existsByChequeNumberIgnoreCaseAndCancelledFalse(String chequeNumber);
    /** Any cheque ever raised with this number, cancelled ones included (a spoilt leaf is never reused). */
    boolean existsByChequeNumberIgnoreCase(String chequeNumber);
    List<Cheque> findByChequeDateBetweenOrderByChequeDateDescChequeIdDesc(LocalDate from, LocalDate to);
}
