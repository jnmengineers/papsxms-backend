package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Guardian;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {
    List<Guardian> findAllByOrderByFullNameAsc();
    Optional<Guardian> findByPhone(String phone);
}
