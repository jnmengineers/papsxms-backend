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

    public record StartIn(Long examId, Long classId, Long subjectId) {}

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
        if (in == null || in.examId() == null || in.classId() == null || in.subjectId() == null) throw bad("Choose the exam, class and subject you marked.");
        var user = userRepository.findByUsername(me.getUsername()).orElseThrow(() -> bad("Please log in again."));
        var exam = examRepository.findById(in.examId()).orElseThrow(() -> bad("Exam not found."));
        var cls = classRepository.findById(in.classId()).orElseThrow(() -> bad("Class not found."));
        var sub = subjectRepository.findById(in.subjectId()).orElseThrow(() -> bad("Subject not found."));
        boolean dup = repo.findByUsernameAndStoppedAtIsNullAndValidUntilGreaterThanEqual(me.getUsername(), LocalDate.now()).stream()
                .anyMatch(i -> i.getExamId().equals(exam.getExamId()) && i.getClassId().equals(cls.getClassId()) && i.getSubjectId().equals(sub.getSubjectId()));
        if (dup) throw bad("You are already in invigilation mode for this exam, class and subject.");
        Invigilation i = new Invigilation();
        i.setUsername(user.getUsername()); i.setTeacherName(MarkEntryCoverService.displayName(user));
        i.setExamId(exam.getExamId()); i.setExamName(exam.getExamName() + " T" + exam.getTerm() + " " + exam.getAcademicYear());
        i.setClassId(cls.getClassId()); i.setClassName(cls.getClassName() + (cls.getStream() != null ? " " + cls.getStream() : ""));
        i.setSubjectId(sub.getSubjectId()); i.setSubjectName(sub.getSubjectName());
        i.setValidUntil(LocalDate.now().plusDays(DAYS)); i.setCreatedAt(LocalDateTime.now());
        repo.save(i);
        return Map.of("message", "Invigilation mode on: you can fill in empty " + i.getSubjectName() + " marks for " + i.getClassName() + " (" + i.getExamName() + ") until " + i.getValidUntil() + ".");
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
