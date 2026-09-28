package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.StreamSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreamSettingRepository extends JpaRepository<StreamSetting, String> {
}
