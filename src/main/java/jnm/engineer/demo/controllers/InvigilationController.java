package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.Invigilation;
import jnm.engineer.demo.repositories.*;
import jnm.engineer.demo.security.AccessGuard;
import jnm.engineer.demo.services.MarkEntryCoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Invigilation / marking mode (see Invigilation). Teachers and admin — SecurityConfig. */
@RestController
@RequestMapping("/api/invigilation")
@RequiredArgsConstructor
public class InvigilationController {
    private static final int DAYS = 14;
    private final InvigilationRepository repo;
    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final SchoolClassRepository classRepository;
    private final SubjectRepository subjectRepository;
    private final AccessGuard accessGuard;

    public record StartIn(Long examId, Long classId, Long subjectId, List<Long> subjectIds) {}

    private static boolean admin(UserDetails me) { return me.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())); }

    /** Every active invigilation (open to all teachers: transparency), with who may stop it. */
    @GetMapping
    public List<Map<String, Object>> active(@AuthenticationPrincipal UserDetails me) {
        var scope = accessGuard.currentScope();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Invigilation i : repo.findByStoppedAtIsNullAndValidUntilGreaterThanEqualOrderByCreatedAtDesc(LocalDate.now())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId()); m.put("teacherName", i.getTeacherName()); m.put("examName", i.getExamName());
            m.put("className", i.getClassName()); m.put("subjectName", i.getSubjectName()); m.put("validUntil", i.getValidUntil().toString());
            boolean mine = i.getUsername().equalsIgnoreCase(me.getUsername());
            m.put("mine", mine);
            m.put("canStop", mine || admin(me) || scope.isOwnClass(i.getClassId()));
            out.add(m);
        }
        return out;
    }

    @PostMapping
    public Map<String, Object> start(@RequestBody StartIn in, @AuthenticationPrincipal UserDetails me) {
        Set<Long> ids = new LinkedHashSet<>();
        if (in != null && in.subjectIds() != null) in.subjectIds().stream().filter(Objects::nonNull).forEach(ids::add);
        if (in != null && in.subjectId() != null) ids.add(in.subjectId());
        if (in == null || in.examId() == null || in.classId() == null || ids.isEmpty()) throw bad("Choose the exam, class and the subject(s) you marked.");
        if (ids.size() > 15) throw bad("Choose at most 15 subjects.");
        var user = userRepository.findByUsername(me.getUsername()).orElseThrow(() -> bad("Please log in again."));
        var exam = examRepository.findById(in.examId()).orElseThrow(() -> bad("Exam not found."));
        var cls = classRepository.findById(in.classId()).orElseThrow(() -> bad("Class not found."));
        var active = repo.findByUsernameAndStoppedAtIsNullAndValidUntilGreaterThanEqual(me.getUsername(), LocalDate.now());
        List<String> started = new ArrayList<>();
        for (Long sid : ids) {
            var sub = subjectRepository.findById(sid).orElseThrow(() -> bad("Subject not found."));
            boolean dup = active.stream().anyMatch(i -> i.getExamId().equals(exam.getExamId()) && i.getClassId().equals(cls.getClassId()) && i.getSubjectId().equals(sid));
            if (dup) continue;   // already on for this subject
            Invigilation i = new Invigilation();
            i.setUsername(user.getUsername()); i.setTeacherName(MarkEntryCoverService.displayName(user));
            i.setExamId(exam.getExamId()); i.setExamName(exam.getExamName() + " T" + exam.getTerm() + " " + exam.getAcademicYear());
            i.setClassId(cls.getClassId()); i.setClassName(cls.getClassName() + (cls.getStream() != null ? " " + cls.getStream() : ""));
            i.setSubjectId(sid); i.setSubjectName(sub.getSubjectName());
            i.setValidUntil(LocalDate.now().plusDays(DAYS)); i.setCreatedAt(LocalDateTime.now());
            repo.save(i);
            started.add(sub.getSubjectName());
        }
        if (started.isEmpty()) throw bad("You are already in invigilation mode for these subjects.");
        String cName = cls.getClassName() + (cls.getStream() != null ? " " + cls.getStream() : "");
        return Map.of("message", "Invigilation mode on for " + cName + " (" + exam.getExamName() + "): " + String.join(", ", started)
                + ". You can fill in empty marks for these subjects until " + LocalDate.now().plusDays(DAYS) + ".");
    }

    @PostMapping("/{id}/stop")
    public Map<String, Object> stop(@PathVariable Long id, @AuthenticationPrincipal UserDetails me) {
        Invigilation i = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found."));
        boolean allowed = i.getUsername().equalsIgnoreCase(me.getUsername()) || admin(me) || accessGuard.currentScope().isOwnClass(i.getClassId());
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the teacher, the class teacher or the admin can stop this.");
        i.setStoppedAt(LocalDateTime.now()); i.setStoppedBy(me.getUsername());
        repo.save(i);
        return Map.of("message", "Invigilation mode stopped.");
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}