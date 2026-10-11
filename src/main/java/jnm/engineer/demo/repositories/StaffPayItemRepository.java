package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.StaffPayItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffPayItemRepository extends JpaRepository<StaffPayItem, Long> {
    List<StaffPayItem> findByStaffStaffId(Long staffId);
}
