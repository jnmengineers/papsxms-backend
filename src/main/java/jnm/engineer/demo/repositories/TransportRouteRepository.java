package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.TransportRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransportRouteRepository extends JpaRepository<TransportRoute, Long> {
    List<TransportRoute> findAllByOrderByActiveDescNameAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndRouteIdNot(String name, Long routeId);
}
