package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findBySchoolClassClassIdAndDate(Long classId, LocalDate date);

    /** Every class's register for one day (for the daily school attendance record). */
    List<AttendanceRecord> findByDate(LocalDate date);

    List<AttendanceRecord> findBySchoolClassClassIdAndDateBetween(Long classId, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByStudentStudentIdAndDateBetweenOrderByDateDesc(Long studentId, LocalDate from, LocalDate to);
}
