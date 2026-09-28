package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByMethodAndReferenceIgnoreCaseAndReversedFalse(Payment.Method method, String reference);
    List<Payment> findByStudentStudentIdOrderByPaidOnAscPaymentIdAsc(Long studentId);
    List<Payment> findByPaidOnBetweenOrderByPaidOnDescPaymentIdDesc(LocalDate from, LocalDate to);

    /** [studentId, total paid] — reversed payments don't count */
    @Query("select p.student.studentId, sum(p.amount) from Payment p where p.reversed = false group by p.student.studentId")
    List<Object[]> totalsByStudent();
}
