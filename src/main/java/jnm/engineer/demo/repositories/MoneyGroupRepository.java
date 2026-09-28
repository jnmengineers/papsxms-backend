package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.MoneyGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MoneyGroupRepository extends JpaRepository<MoneyGroup, Long> {
    List<MoneyGroup> findAllByOrderByPriorityAscNameAsc();
    boolean existsByNameIgnoreCaseAndGroupIdNot(String name, Long groupId);
    boolean existsByNameIgnoreCase(String name);
}
