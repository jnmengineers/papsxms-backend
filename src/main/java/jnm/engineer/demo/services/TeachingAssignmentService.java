package jnm.engineer.demo.services;

import jakarta.persistence.EntityManager;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Subject;
import jnm.engineer.demo.models.Teacher;
import jnm.engineer.demo.models.MarkEntryCover;
import jnm.engineer.demo.models.TeachingAssignment;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.MarkEntryCoverRepository;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.repositories.UserRepository;
import jnm.engineer.demo.repositories.TeachingAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

/** Who teaches which subject to which class (Upper Primary & Junior School). One teacher per subject per class. */
@Service
@RequiredArgsConstructor
public class TeachingAssignmentService {

    private final TeachingAssignmentRepository repository;
    private final EntityManager entityManager;
    private final UserRepository userRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final MarkEntryCoverRepository coverRepository;

    @Transactional(readOnly = true)
    public List<TeachingAssignment> getAll() { return repository.findAll(); }

    @Transactional(readOnly = true)
    public List<TeachingAssignment> byTeacher(Long teacherId) { return repository.findByTeacherTeacherId(teacherId); }

    @Transactional(readOnly = true)
    public List<TeachingAssignment> byClass(Long classId) { return repository.findBySchoolClassClassId(classId); }

    /**
     * The logged-in teacher's classes for Mark Entry:
     *   classTeacher = true  → their own class: every subject
     *   classTeacher = false → a class they teach a subject in: only subjectIds
     *   cover = true         → covering for a colleague: coverAll (every subject) or coverSubjectIds
     * Uses the same rules as AccessGuard, so the page and the server always agree.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in."));
        Map<Long, Map<String, Object>> byClass = new LinkedHashMap<>();

        List<SchoolClass> own = new ArrayList<>();
        if (user.getTeacher() != null) own.addAll(schoolClassRepository.findByClassTeacherTeacherId(user.getTeacher().getTeacherId()));
        if (user.getLinkedClass() != null && own.stream().noneMatch(c -> c.getClassId().equals(user.getLinkedClass().getClassId()))) {
            own.add(user.getLinkedClass());
        }
        for (SchoolClass c : own) {
            Map<String, Object> m = classMap(c);
            m.put("classTeacher", true);
            m.put("subjectIds", List.of());
            byClass.put(c.getClassId(), m);
        }

        Long teacherId = user.getTeacher() != null ? user.getTeacher().getTeacherId() : user.getLinkedId();
        if (teacherId != null) {
            for (TeachingAssignment ta : repository.findByTeacherTeacherId(teacherId)) {
                SchoolClass c = ta.getSchoolClass();
                Map<String, Object> m = byClass.computeIfAbsent(c.getClassId(), k -> {
                    Map<String, Object> x = classMap(c);
                    x.put("classTeacher", false);
                    x.put("subjectIds", new ArrayList<Long>());
                    return x;
                });
                if (!(Boolean) m.get("classTeacher")) {
                    @SuppressWarnings("unchecked") List<Long> ids = (List<Long>) m.get("subjectIds");
                    ids.add(ta.getSubject().getSubjectId());
                    @SuppressWarnings("unchecked") List<String> names = (List<String>) m.computeIfAbsent("subjectNames", k -> new ArrayList<String>());
                    names.add(ta.getSubject().getSubjectName());
                }
            }
        }
        // Covering for a colleague (active today) — marks only
        for (MarkEntryCover c : coverRepository.findByCoverUserUserIdAndRevokedFalseAndValidUntilGreaterThanEqual(user.getUserId(), LocalDate.now())) {
            SchoolClass sc = c.getSchoolClass();
            Map<String, Object> m = byClass.computeIfAbsent(sc.getClassId(), k -> {
                Map<String, Object> x = classMap(sc);
                x.put("classTeacher", false);
                x.put("subjectIds", new ArrayList<Long>());
                return x;
            });
            if ((Boolean) m.get("classTeacher")) continue;          // already has every subject
            m.put("cover", true);
            m.put("coverFor", c.getGrantedByName());
            String until = c.getValidUntil().toString();
            if (m.get("coverUntil") == null || until.compareTo(String.valueOf(m.get("coverUntil"))) > 0) m.put("coverUntil", until);
            if (c.getSubject() == null) m.put("coverAll", true);
            else {
                @SuppressWarnings("unchecked") List<Long> ids = (List<Long>) m.computeIfAbsent("coverSubjectIds", k -> new ArrayList<Long>());
                ids.add(c.getSubject().getSubjectId());
                @SuppressWarnings("unchecked") List<String> names = (List<String>) m.computeIfAbsent("coverSubjectNames", k -> new ArrayList<String>());
                names.add(c.getSubject().getSubjectName());
            }
        }

        List<Map<String, Object>> out = new ArrayList<>(byClass.values());
        out.sort(Comparator.comparing((Map<String, Object> m) -> !(Boolean) m.get("classTeacher"))
                .thenComparing(m -> String.valueOf(m.get("className"))));
        return out;
    }

    private static Map<String, Object> classMap(SchoolClass c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("classId", c.getClassId());
        m.put("className", c.getClassName());
        m.put("stream", c.getStream());
        m.put("gradeLevel", c.getGradeLevel());
        m.put("section", c.getSection());
        return m;
    }

    /** Sets the teacher for a class + subject, replacing any existing one. */
    @Transactional
    public TeachingAssignment assign(Long classId, Long subjectId, Long teacherId) {
        SchoolClass schoolClass = find(SchoolClass.class, classId, "Class");
        Subject subject = find(Subject.class, subjectId, "Subject");
        Teacher teacher = find(Teacher.class, teacherId, "Teacher");
        TeachingAssignment a = repository.findBySchoolClassClassIdAndSubjectSubjectId(classId, subjectId)
                .orElseGet(TeachingAssignment::new);
        a.setSchoolClass(schoolClass);
        a.setSubject(subject);
        a.setTeacher(teacher);
        return repository.save(a);
    }

    @Transactional
    public void remove(Long classId, Long subjectId) {
        repository.findBySchoolClassClassIdAndSubjectSubjectId(classId, subjectId).ifPresent(repository::delete);
    }

    private <T> T find(Class<T> type, Long id, String label) {
        T entity = entityManager.find(type, id);
        if (entity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, label + " " + id + " not found");
        return entity;
    }
}
