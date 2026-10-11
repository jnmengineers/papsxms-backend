package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.MpesaFloatEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MpesaFloatEntryRepository extends JpaRepository<MpesaFloatEntry, Long> {
    List<MpesaFloatEntry> findByCancelledFalse();
    Optional<MpesaFloatEntry> findByChequeIdAndCancelledFalse(Long chequeId);
}
