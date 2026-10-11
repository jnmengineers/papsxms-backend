package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.PayrollSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollSettingsRepository extends JpaRepository<PayrollSettings, Long> {
}
