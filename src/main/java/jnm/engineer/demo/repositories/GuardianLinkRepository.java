package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.GuardianLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GuardianLinkRepository extends JpaRepository<GuardianLink, Long> {
    List<GuardianLink> findByGuardianGuardianId(Long guardianId);
    List<GuardianLink> findByStudentId(Long studentId);
    Optional<GuardianLink> findByGuardianGuardianIdAndStudentId(Long guardianId, Long studentId);
}
