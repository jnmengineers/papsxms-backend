package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.SectionSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionSettingRepository extends JpaRepository<SectionSetting, String> {
}
