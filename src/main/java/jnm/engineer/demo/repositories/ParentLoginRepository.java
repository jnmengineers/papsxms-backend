package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.ParentLogin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ParentLoginRepository extends JpaRepository<ParentLogin, Long> {
    Optional<ParentLogin> findFirstByPhoneOrderByCreatedAtDesc(String phone);
    Optional<ParentLogin> findByToken(String token);
    long countByPhoneAndCreatedAtAfter(String phone, LocalDateTime after);
}
