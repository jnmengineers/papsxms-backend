package jnm.engineer.demo.services;

import jakarta.persistence.EntityManager;
import jnm.engineer.demo.models.MarkEntryCover;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Subject;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.MarkEntryCoverRepository;
import jnm.engineer.demo.repositories.UserRepository;
import jnm.engineer.demo.security.AccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Covering for a colleague: temporary permission to enter MARKS for a class.
 *   ADMIN            → may arrange any cover
 *   Class teacher    → may hand over their class (all subjects or one subject)
 *   Subject teacher  → may hand over a subject they teach in that class
 * Covers end by themselves after validUntil; they can be cancelled early by the admin,
 * the person who arranged it, or the covering teacher (handing it back).
 */
@Service
@RequiredArgsConstructor
public class MarkEntryCoverService {

    private static final int MAX_DAYS = 60;
    private static final DateTimeFormatter NICE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final MarkEntryCoverRepository coverRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;
    private final EntityManager entityManager;

    public record GrantIn(Long coverUserId, Long classId, Long subjectId, LocalDate validUntil, String note) {}

    /** Teacher logins that can be chosen to cover (everyone with the TEACHER role except me). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> colleagues(String me) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : userRepository.findByRole(User.Role.TEACHER)) {
            if (u.getUsername().equalsIgnoreCase(me)) continue;
            out.add(Map.of("userId", u.getUserId(), "name", displayName(u)));
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("name"))));
        return out;
    }

    /** Admin: every active cover. Teacher: covers they arranged or hold. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> active(String me, boolean admin) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (MarkEntryCover c : coverRepository.findByRevokedFalseAndValidUntilGreaterThanEqualOrderByValidUntilAsc(LocalDate.now())) {
            boolean mine = c.getCoverUser().getUsername().equalsIgnoreCase(me);
            boolean gaveIt = me.equalsIgnoreCase(c.getGrantedBy());
            if (!admin && !mine && !gaveIt) continue;
            Map<String, Object> m = coverMap(c);
            m.put("heldByMe", mine);
            m.put("givenByMe", gaveIt);
            m.put("canCancel", admin || mine || gaveIt);
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> grant(GrantIn in, String me, boolean admin) {
        if (in.coverUserId() == null) throw bad("Choose the colleague who will enter the marks.");
        User cover = userRepository.findById(in.coverUserId()).orElseThrow(() -> notFound("Colleague not found."));
        if (cover.getRole() != User.Role.TEACHER) throw bad("Only teacher logins can cover.");
        if (cover.getUsername().equalsIgnoreCase(me)) throw bad("You can't arrange cover for yourself.");
        SchoolClass cls = in.classId() == null ? null : entityManager.find(SchoolClass.class, in.classId());
        if (cls == null) throw bad("Choose the class.");
        Subject subject = in.subjectId() == null ? null : entityManager.find(Subject.class, in.subjectId());
        if (in.subjectId() != null && subject == null) throw notFound("Subject not found.");

        LocalDate today = LocalDate.now();
        LocalDate until = in.validUntil() != null ? in.validUntil() : today.plusDays(7);
        if (until.isBefore(today)) throw bad("The end date can't be in the past.");
        if (until.isAfter(today.plusDays(MAX_DAYS))) throw bad("Cover can last at most " + MAX_DAYS + " days.");

        // A teacher can only hand over what they themselves may do
        if (!admin) {
            AccessGuard.Scope scope = accessGuard.currentScope();
            if (subject == null && !scope.isOwnClass(cls.getClassId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the class teacher can hand over all subjects of " + cls.getClassName() + ".");
            }
            if (subject != null && !scope.teachesSubject(cls.getClassId(), subject.getSubjectId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only hand over subjects you teach in " + cls.getClassName() + ".");
            }
        }

        User granter = userRepository.findByUsername(me).orElse(null);
        MarkEntryCover c = new MarkEntryCover();
        c.setCoverUser(cover);
        c.setSchoolClass(cls);
        c.setSubject(subject);
        c.setValidUntil(until);
        String note = in.note() == null ? null : in.note().trim();
        c.setNote(note == null || note.isEmpty() ? null : (note.length() > 150 ? note.substring(0, 150) : note));
        c.setGrantedBy(me);
        c.setGrantedByName(granter != null ? displayName(granter) : me);
        c.setCreatedAt(LocalDateTime.now());
        coverRepository.save(c);

        Map<String, Object> out = new LinkedHashMap<>(coverMap(c));
        out.put("message", displayName(cover) + " can enter " + cls.getClassName() + " "
                + (subject == null ? "marks (all subjects)" : subject.getSubjectName() + " marks")
                + " until " + until.format(NICE) + ".");
        return out;
    }

    @Transactional
    public Map<String, Object> cancel(Long coverId, String me, boolean admin) {
        MarkEntryCover c = coverRepository.findById(coverId).orElseThrow(() -> notFound("Cover not found."));
        boolean allowed = admin || me.equalsIgnoreCase(c.getGrantedBy()) || c.getCoverUser().getUsername().equalsIgnoreCase(me);
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the admin, whoever arranged it, or the covering teacher can cancel this.");
        if (!c.isRevoked()) {
            c.setRevoked(true);
            c.setRevokedBy(me);
            c.setRevokedAt(LocalDateTime.now());
            coverRepository.save(c);
        }
        return Map.of("message", "Cover cancelled.");
    }

    // ── helpers ──
    public static String displayName(User u) {
        if (u.getTeacher() != null) return u.getTeacher().getFirstName() + " " + u.getTeacher().getLastName();
        return u.getUsername();
    }

    private static Map<String, Object> coverMap(MarkEntryCover c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("coverId", c.getCoverId());
        m.put("coverUserId", c.getCoverUser().getUserId());
        m.put("coverName", displayName(c.getCoverUser()));
        m.put("classId", c.getSchoolClass().getClassId());
        m.put("className", c.getSchoolClass().getClassName());
        m.put("stream", c.getSchoolClass().getStream());
        m.put("subjectId", c.getSubject() != null ? c.getSubject().getSubjectId() : null);
        m.put("subjectName", c.getSubject() != null ? c.getSubject().getSubjectName() : "All subjects");
        m.put("validUntil", c.getValidUntil().toString());
        m.put("grantedByName", c.getGrantedByName());
        m.put("note", c.getNote());
        return m;
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
}
