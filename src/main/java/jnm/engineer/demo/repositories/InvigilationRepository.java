package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Invigilation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface InvigilationRepository extends JpaRepository<Invigilation, Long> {
    List<Invigilation> findByStoppedAtIsNullAndValidUntilGreaterThanEqualOrderByCreatedAtDesc(LocalDate today);
    List<Invigilation> findByUsernameAndStoppedAtIsNullAndValidUntilGreaterThanEqual(String username, LocalDate today);
}
