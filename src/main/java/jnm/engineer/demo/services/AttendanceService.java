package jnm.engineer.demo.services;

import jakarta.persistence.EntityManager;
import jnm.engineer.demo.models.AttendanceRecord;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Student;
import jnm.engineer.demo.repositories.AttendanceRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Attendance rules: taking the register, the per-learner summary and a learner's history.
 * WHO may do this (class teacher / admin) is checked in AttendanceController via AccessGuard.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordRepository repository;
    private final StudentService studentService;
    private final EntityManager entityManager;

    /** One line of the register as sent by the page. */
    public record Entry(Long studentId, String status, String note) {}

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDay(Long classId, LocalDate date) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AttendanceRecord r : repository.findBySchoolClassClassIdAndDate(classId, date)) out.add(toMap(r));
        return out;
    }

    /** Saves the whole day's register. If any line is wrong, nothing is saved. */
    @Transactional
    public Map<String, Object> saveDay(Long classId, LocalDate date, List<Entry> entries, String who) {
        if (date.isAfter(LocalDate.now())) throw bad("You can't take the register for a future date.");
        SchoolClass schoolClass = entityManager.find(SchoolClass.class, classId);
        if (schoolClass == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found.");

        // Only learners currently in this class may be marked
        Map<Long, Student> classStudents = new HashMap<>();
        for (Student s : studentService.GetByClass(classId)) classStudents.put(s.getStudentId(), s);

        Map<Long, AttendanceRecord> existing = new HashMap<>();
        for (AttendanceRecord r : repository.findBySchoolClassClassIdAndDate(classId, date)) {
            existing.put(r.getStudent().getStudentId(), r);
        }

        List<String> problems = new ArrayList<>();
        List<AttendanceRecord> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Entry e : entries == null ? List.<Entry>of() : entries) {
            Student student = classStudents.get(e.studentId());
            if (student == null) { problems.add("Student " + e.studentId() + " is not in this class"); continue; }
            AttendanceRecord.Status status;
            try { status = AttendanceRecord.Status.valueOf(String.valueOf(e.status()).trim().toUpperCase()); }
            catch (IllegalArgumentException ex) {
                problems.add(student.getFirstName() + " " + student.getLastName() + ": unknown status \"" + e.status() + "\"");
                continue;
            }
            AttendanceRecord r = existing.getOrDefault(e.studentId(), new AttendanceRecord());
            r.setStudent(student);
            r.setSchoolClass(schoolClass);
            r.setDate(date);
            r.setStatus(status);
            String note = e.note() == null ? null : e.note().trim();
            r.setNote(note == null || note.isEmpty() ? null : (note.length() > 200 ? note.substring(0, 200) : note));
            r.setMarkedBy(who);
            r.setMarkedAt(now);
            toSave.add(r);
        }
        if (!problems.isEmpty()) throw bad("Register not saved: " + String.join("; ", problems));
        repository.saveAll(toSave);
        return Map.of("message", "Register saved for " + toSave.size() + " student(s).", "saved", toSave.size());
    }

    /** Per-learner totals and % for a date range. Late counts as attended. */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(Long classId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw bad("The start date must be before the end date.");
        Set<LocalDate> daysTaken = new TreeSet<>();
        Map<Long, int[]> counts = new LinkedHashMap<>();   // [present, absent, late, excused]
        Map<Long, Student> students = new LinkedHashMap<>();
        for (Student s : studentService.GetByClass(classId)) { students.put(s.getStudentId(), s); counts.put(s.getStudentId(), new int[4]); }

        for (AttendanceRecord r : repository.findBySchoolClassClassIdAndDateBetween(classId, from, to)) {
            daysTaken.add(r.getDate());
            Long sid = r.getStudent().getStudentId();
            students.putIfAbsent(sid, r.getStudent());      // includes learners who have since moved out
            counts.computeIfAbsent(sid, k -> new int[4])[r.getStatus().ordinal()]++;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, int[]> en : counts.entrySet()) {
            Student s = students.get(en.getKey());
            int[] c = en.getValue();
            int marked = c[0] + c[1] + c[2] + c[3];
            int attended = c[0] + c[2];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", s.getStudentId());
            row.put("admissionNumber", s.getAdmissionNumber());
            row.put("name", s.getFirstName() + " " + s.getLastName());
            row.put("present", c[0]);
            row.put("absent", c[1]);
            row.put("late", c[2]);
            row.put("excused", c[3]);
            row.put("daysMarked", marked);
            row.put("percent", marked == 0 ? null : Math.round(attended * 1000.0 / marked) / 10.0);
            rows.add(row);
        }
        return Map.of("daysTaken", daysTaken.size(), "from", from.toString(), "to", to.toString(), "students", rows);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> byStudent(Long studentId, LocalDate from, LocalDate to) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AttendanceRecord r : repository.findByStudentStudentIdAndDateBetweenOrderByDateDesc(studentId, from, to)) out.add(toMap(r));
        return out;
    }

    private static Map<String, Object> toMap(AttendanceRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("attendanceId", r.getAttendanceId());
        m.put("studentId", r.getStudent().getStudentId());
        m.put("classId", r.getSchoolClass().getClassId());
        m.put("date", r.getDate().toString());
        m.put("status", r.getStatus().name());
        m.put("note", r.getNote());
        m.put("markedBy", r.getMarkedBy());
        m.put("markedAt", r.getMarkedAt() != null ? r.getMarkedAt().toString() : null);
        return m;
    }

    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
