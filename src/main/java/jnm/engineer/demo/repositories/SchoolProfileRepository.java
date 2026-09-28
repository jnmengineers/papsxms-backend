package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.SchoolProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolProfileRepository extends JpaRepository<SchoolProfile, Long> {
}
