package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.SmsSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsSettingsRepository extends JpaRepository<SmsSettings, Long> {
}
