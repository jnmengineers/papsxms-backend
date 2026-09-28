package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.OptionalCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OptionalChargeRepository extends JpaRepository<OptionalCharge, Long> {
    List<OptionalCharge> findAllByOrderByActiveDescNameAsc();
    boolean existsByNameIgnoreCaseAndSectionsAndChargeIdNot(String name, String sections, Long chargeId);
    boolean existsByNameIgnoreCaseAndSections(String name, String sections);
}
