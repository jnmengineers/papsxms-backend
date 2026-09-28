package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.TransportSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TransportSubscriptionRepository extends JpaRepository<TransportSubscription, Long> {
    List<TransportSubscription> findByYearLabelAndTermAndCancelledFalse(String yearLabel, Integer term);
    List<TransportSubscription> findByStudentStudentIdOrderByCreatedAtAsc(Long studentId);

    /** [studentId, total active transport charges] */
    @Query("select t.student.studentId, sum(t.amount) from TransportSubscription t where t.cancelled = false group by t.student.studentId")
    List<Object[]> totalsByStudent();
}
