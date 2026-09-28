package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.FeeItemGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeeItemGroupRepository extends JpaRepository<FeeItemGroup, Long> {
    Optional<FeeItemGroup> findByItemNameIgnoreCase(String itemName);
    long countByGroupGroupId(Long groupId);
}
