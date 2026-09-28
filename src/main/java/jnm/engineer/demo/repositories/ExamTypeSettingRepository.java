package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.ExamTypeSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamTypeSettingRepository extends JpaRepository<ExamTypeSetting, String> {
}
