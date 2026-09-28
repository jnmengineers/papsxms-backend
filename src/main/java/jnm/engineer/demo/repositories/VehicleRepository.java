package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findAllByOrderByActiveDescNameAscRegistrationAsc();
    boolean existsByRegistrationIgnoreCase(String registration);
    boolean existsByRegistrationIgnoreCaseAndVehicleIdNot(String registration, Long vehicleId);
}
