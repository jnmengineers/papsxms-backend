package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.DutyKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DutyKindRepository extends JpaRepository<DutyKind, String> {
    List<DutyKind> findAllByOrderBySortOrderAscNameAsc();
}
