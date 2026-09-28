package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.ChargeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChargeEntryRepository extends JpaRepository<ChargeEntry, Long> {
    List<ChargeEntry> findByChargeChargeIdAndCancelledFalse(Long chargeId);
    List<ChargeEntry> findByChargeChargeIdAndYearLabelAndTermAndCancelledFalse(Long chargeId, String yearLabel, Integer term);
    List<ChargeEntry> findByStudentStudentIdOrderByCreatedAtAsc(Long studentId);

    /** [studentId, total of active extra charges] */
    @Query("select c.student.studentId, sum(c.amount) from ChargeEntry c where c.cancelled = false group by c.student.studentId")
    List<Object[]> totalsByStudent();
}
