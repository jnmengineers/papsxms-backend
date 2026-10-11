package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, String> {
    List<LeaveType> findAllByOrderBySortOrderAscNameAsc();
}
