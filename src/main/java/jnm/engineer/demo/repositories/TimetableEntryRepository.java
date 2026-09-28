package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.TimetableEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Long> {
    List<TimetableEntry> findBySchoolClassClassId(Long classId);
    List<TimetableEntry> findByTeacherTeacherId(Long teacherId);
    List<TimetableEntry> findBySlotSlotId(Long slotId);
    List<TimetableEntry> findByTeacherIsNotNull();
}
