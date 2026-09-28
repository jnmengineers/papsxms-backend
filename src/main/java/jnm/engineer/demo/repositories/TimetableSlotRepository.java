package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.TimetableSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, Long> {
    List<TimetableSlot> findBySectionOrderByPositionAsc(String section);
    List<TimetableSlot> findAllByOrderBySectionAscPositionAsc();
}
