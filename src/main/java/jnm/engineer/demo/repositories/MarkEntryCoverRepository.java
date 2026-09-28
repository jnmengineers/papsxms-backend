package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.MarkEntryCover;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MarkEntryCoverRepository extends JpaRepository<MarkEntryCover, Long> {
    /** Covers held by one teacher that are still active today. */
    List<MarkEntryCover> findByCoverUserUserIdAndRevokedFalseAndValidUntilGreaterThanEqual(Long userId, LocalDate today);

    /** Every cover still active today. */
    List<MarkEntryCover> findByRevokedFalseAndValidUntilGreaterThanEqualOrderByValidUntilAsc(LocalDate today);
}
