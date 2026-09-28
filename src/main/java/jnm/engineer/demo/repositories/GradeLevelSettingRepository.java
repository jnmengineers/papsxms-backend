package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.GradeLevelSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeLevelSettingRepository extends JpaRepository<GradeLevelSetting, String> {
}
