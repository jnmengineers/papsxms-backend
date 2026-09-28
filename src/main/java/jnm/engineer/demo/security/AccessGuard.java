package jnm.engineer.demo.security;

import jakarta.persistence.EntityManager;
import jnm.engineer.demo.models.MarkEntryCover;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Student;
import jnm.engineer.demo.models.TeachingAssignment;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.MarkEntryCoverRepository;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.repositories.TeachingAssignmentRepository;
import jnm.engineer.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

/**
 * One place that answers "may the logged-in user see / change this class's data?"
 *
 *   ADMIN, CLERK ..... every class
 *   TEACHER .......... read:  their own class(es) + classes they teach a subject in
 *                      write: every subject in their own class(es),
 *                             only their own subject in other classes
 *
 * "Own class" = classes where this login's teacher is the class teacher
 * (SchoolClass.classTeacher), plus the login's linkedClass (kept in step with it).
 *
 * COVER (MarkEntryCover): a teacher covering for a colleague may enter MARKS for that
 * class — one subject, or all subjects — until the cover's end date. Covers never allow
 * changing learners, report cards or the register (those check canWrite(classId, null)).
 *
 * Controllers call currentScope() once per request and then ask it questions.
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    private final UserRepository userRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final MarkEntryCoverRepository coverRepository;
    private final EntityManager entityManager;

    /** What the current user may touch. */
    public static final class Scope {
        private final boolean all;
        private final Set<Long> ownClassIds;
        private final Set<Long> readableClassIds;
        private final Map<Long, Set<Long>> subjectsByClass;
        private final Set<Long> coverAllClassIds;                // covers for every subject: marks only
        private final Map<Long, Set<Long>> coverSubjectsByClass; // covers for one subject: marks only

        Scope(boolean all, Set<Long> ownClassIds, Set<Long> readableClassIds, Map<Long, Set<Long>> subjectsByClass,
              Set<Long> coverAllClassIds, Map<Long, Set<Long>> coverSubjectsByClass) {
            this.all = all;
            this.ownClassIds = ownClassIds;
            this.readableClassIds = readableClassIds;
            this.subjectsByClass = subjectsByClass;
            this.coverAllClassIds = coverAllClassIds;
            this.coverSubjectsByClass = coverSubjectsByClass;
        }

        /** Class teacher of this class (not counting covers). */
        public boolean isOwnClass(Long classId) { return all || (classId != null && ownClassIds.contains(classId)); }

        /** Teaches this subject in this class by assignment (not counting covers). */
        public boolean teachesSubject(Long classId, Long subjectId) {
            return all || isOwnClass(classId) || (subjectId != null && subjectsByClass.getOrDefault(classId, Set.of()).contains(subjectId));
        }

        public boolean isAll() { return all; }

        public boolean canRead(Long classId) {
            return all || (classId != null && readableClassIds.contains(classId));
        }

        public boolean canWrite(Long classId, Long subjectId) {
            if (all) return true;
            if (classId == null) return false;
            if (ownClassIds.contains(classId)) return true;              // class teacher: every subject
            if (subjectId == null) return false;                         // learners/report cards/register: own class only
            if (coverAllClassIds.contains(classId)) return true;         // covering every subject: marks only
            if (coverSubjectsByClass.getOrDefault(classId, Set.of()).contains(subjectId)) return true;
            return subjectsByClass.getOrDefault(classId, Set.of()).contains(subjectId);
        }

        public Set<Long> readableClassIds() { return Collections.unmodifiableSet(readableClassIds); }

        /** Classes where this user is class teacher (empty for admin/bursar/clerk — use isAll()). */
        public Set<Long> ownClassIds() { return Collections.unmodifiableSet(ownClassIds); }
    }

    public Scope currentScope() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in.");
        }
        Set<String> roles = new HashSet<>();
        for (GrantedAuthority a : auth.getAuthorities()) roles.add(a.getAuthority());
        // Admin, clerk (secretary) and accountant (bursar) see every class. What each may CHANGE
        // is limited by SecurityConfig (e.g. the accountant can't change marks or students).
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_CLERK") || roles.contains("ROLE_ACCOUNTANT")) {
            return new Scope(true, Set.of(), Set.of(), Map.of(), Set.of(), Map.of());
        }
        if (!roles.contains("ROLE_TEACHER")) {
            return new Scope(false, Set.of(), Set.of(), Map.of(), Set.of(), Map.of());   // unknown role: nothing
        }

        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in."));

        Set<Long> own = new HashSet<>();
        if (user.getLinkedClass() != null) own.add(user.getLinkedClass().getClassId());
        // The real source: classes where this login's teacher is class teacher
        if (user.getTeacher() != null) {
            for (SchoolClass c : schoolClassRepository.findByClassTeacherTeacherId(user.getTeacher().getTeacherId())) {
                own.add(c.getClassId());
            }
        }
        Set<Long> readable = new HashSet<>(own);

        // Subject-teacher classes. Uses the proper teacher link; falls back to the old
        // LinkedId only for logins not yet linked (until the migration script has run).
        Map<Long, Set<Long>> subjectsByClass = new HashMap<>();
        Long teacherId = user.getTeacher() != null ? user.getTeacher().getTeacherId() : user.getLinkedId();
        if (teacherId != null) {
            for (TeachingAssignment ta : teachingAssignmentRepository.findByTeacherTeacherId(teacherId)) {
                Long cid = ta.getSchoolClass().getClassId();
                readable.add(cid);
                subjectsByClass.computeIfAbsent(cid, k -> new HashSet<>()).add(ta.getSubject().getSubjectId());
            }
        }
        // Active covers (covering for a colleague) — marks only
        Set<Long> coverAll = new HashSet<>();
        Map<Long, Set<Long>> coverSubjects = new HashMap<>();
        for (MarkEntryCover c : coverRepository.findByCoverUserUserIdAndRevokedFalseAndValidUntilGreaterThanEqual(user.getUserId(), LocalDate.now())) {
            Long cid = c.getSchoolClass().getClassId();
            readable.add(cid);
            if (c.getSubject() == null) coverAll.add(cid);
            else coverSubjects.computeIfAbsent(cid, k -> new HashSet<>()).add(c.getSubject().getSubjectId());
        }
        return new Scope(false, own, readable, subjectsByClass, coverAll, coverSubjects);
    }

    /** The class a student is in (null if the student has no class or doesn't exist). */
    public Long classOfStudent(Long studentId) {
        if (studentId == null) return null;
        Student s = entityManager.find(Student.class, studentId);
        return (s != null && s.getSchoolClass() != null) ? s.getSchoolClass().getClassId() : null;
    }

    /** Looks a class up by name (case-insensitive) among the classes this scope can read. */
    public boolean canReadClassName(Scope scope, String className) {
        if (scope.isAll()) return true;
        if (className == null) return false;
        for (Long id : scope.readableClassIds()) {
            SchoolClass c = entityManager.find(SchoolClass.class, id);
            if (c != null && className.trim().equalsIgnoreCase(String.valueOf(c.getClassName()).trim())) return true;
        }
        return false;
    }

    public static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
