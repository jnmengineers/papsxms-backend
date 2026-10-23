package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.*;
import jnm.engineer.demo.repositories.*;
import jnm.engineer.demo.services.FinanceService;
import jnm.engineer.demo.services.GuardianService;
import jnm.engineer.demo.services.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Parent portal (website/parents.html). Sign-in: phone on record → SMS code → 30-day token
 * (header X-Parent-Token). A parent only ever sees learners linked to them. Under /api/public/**
 * (no staff login) — every child endpoint checks the token and the link.
 */
@RestController
@RequestMapping("/api/public/parent")
@RequiredArgsConstructor
public class ParentPortalController {
    private static final int CODE_MINUTES = 10, MAX_ATTEMPTS = 5, CODES_PER_HOUR = 3, SESSION_DAYS = 30;
    private final ParentLoginRepository logins;
    private final GuardianRepository guardianRepository;
    private final GuardianLinkRepository linkRepository;
    private final StudentRepository studentRepository;
    private final ResultRepository resultRepository;
    private final GradeScaleRepository gradeScaleRepository;
    private final FinanceService financeService;
    private final SmsService smsService;
    private final SecureRandom rnd = new SecureRandom();

    public record PhoneIn(String phone) {}
    public record VerifyIn(String phone, String code) {}

    /** Step 1: send a code. The reply never reveals whether the number is on record. */
    @PostMapping("/request-code")
    public Map<String, String> requestCode(@RequestBody PhoneIn in) {
        String phone = GuardianService.phone(in == null ? null : in.phone());
        String reply = "If this number is registered with the school, a 6-digit code has been sent to it by SMS.";
        if (phone == null) throw bad("Enter your phone number.");
        if (logins.countByPhoneAndCreatedAtAfter(phone, LocalDateTime.now().minusHours(1)) >= CODES_PER_HOUR)
            throw bad("Too many codes requested. Please wait an hour and try again.");
        Guardian g = guardianRepository.findByPhone(phone).orElse(null);
        if (g == null || childrenOf(g).isEmpty()) return Map.of("message", reply);
        String code = String.format("%06d", rnd.nextInt(1_000_000));
        ParentLogin l = new ParentLogin();
        l.setGuardianId(g.getGuardianId()); l.setPhone(phone); l.setCodeHash(sha(phone + ":" + code));
        l.setCodeExpires(LocalDateTime.now().plusMinutes(CODE_MINUTES)); l.setAttempts(0); l.setCreatedAt(LocalDateTime.now());
        logins.save(l);
        smsService.send(List.of(new SmsService.Outgoing(phone, "Your school parent portal code is " + code + ". It expires in " + CODE_MINUTES + " minutes. Do not share it.", g.getGuardianId())), "parent-portal");
        return Map.of("message", reply);
    }

    /** Step 2: confirm the code → session token. */
    @PostMapping("/verify")
    @Transactional
    public Map<String, Object> verify(@RequestBody VerifyIn in) {
        String phone = GuardianService.phone(in == null ? null : in.phone());
        String code = in == null || in.code() == null ? "" : in.code().replaceAll("\\D", "");
        ParentLogin l = phone == null ? null : logins.findFirstByPhoneOrderByCreatedAtDesc(phone).orElse(null);
        if (l == null || l.getCodeHash() == null || l.getCodeExpires() == null || l.getCodeExpires().isBefore(LocalDateTime.now()))
            throw bad("The code has expired or was not requested. Ask for a new code.");
        if (l.getAttempts() != null && l.getAttempts() >= MAX_ATTEMPTS) throw bad("Too many wrong tries. Ask for a new code.");
        if (!sha(phone + ":" + code).equals(l.getCodeHash())) {
            l.setAttempts((l.getAttempts() == null ? 0 : l.getAttempts()) + 1); logins.save(l);
            throw bad("That code is not correct.");
        }
        byte[] b = new byte[32]; rnd.nextBytes(b);
        l.setToken(HexFormat.of().formatHex(b)); l.setTokenExpires(LocalDateTime.now().plusDays(SESSION_DAYS));
        l.setCodeHash(null); l.setLastSeen(LocalDateTime.now()); logins.save(l);
        Guardian g = guardianRepository.findById(l.getGuardianId()).orElseThrow(() -> bad("Account not found."));
        return Map.of("token", l.getToken(), "name", g.getFullName() == null ? "" : g.getFullName());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader(value = "X-Parent-Token", required = false) String token) {
        logins.findByToken(token == null ? "" : token).ifPresent(l -> { l.setToken(null); l.setTokenExpires(null); logins.save(l); });
        return Map.of("message", "Signed out.");
    }

    /** The parent and their children (in school). */
    @GetMapping("/me")
    @Transactional(readOnly = true)
    public Map<String, Object> me(@RequestHeader(value = "X-Parent-Token", required = false) String token) {
        Guardian g = guardian(token);
        List<Map<String, Object>> kids = new ArrayList<>();
        for (Student s : childrenOf(g)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("studentId", s.getStudentId()); m.put("name", (s.getFirstName() + " " + s.getLastName()).trim());
            m.put("admissionNumber", s.getAdmissionNumber());
            m.put("className", s.getSchoolClass() != null ? s.getSchoolClass().getClassName() : s.getClassName());
            m.put("stream", s.getSchoolClass() != null ? s.getSchoolClass().getStream() : s.getStream());
            kids.add(m);
        }
        return Map.of("name", g.getFullName() == null ? "" : g.getFullName(), "children", kids);
    }

    @GetMapping("/child/{studentId}/fees")
    @Transactional(readOnly = true)
    public Map<String, Object> fees(@PathVariable Long studentId, @RequestHeader(value = "X-Parent-Token", required = false) String token) {
        mine(guardian(token), studentId);
        return financeService.statement(studentId);
    }

    /** Marks per exam, with the school's grading bands (for levels). */
    @GetMapping("/child/{studentId}/results")
    @Transactional(readOnly = true)
    public Map<String, Object> results(@PathVariable Long studentId, @RequestHeader(value = "X-Parent-Token", required = false) String token) {
        mine(guardian(token), studentId);
        Map<Long, Map<String, Object>> byExam = new LinkedHashMap<>();
        resultRepository.findByStudentStudentId(studentId).stream()
                .filter(r -> r.getExam() != null && r.getSubject() != null)
                .sorted(Comparator.comparing((Result r) -> r.getExam().getExamId()).reversed())
                .forEach(r -> {
                    Exam e = r.getExam();
                    Map<String, Object> ex = byExam.computeIfAbsent(e.getExamId(), k -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("examId", e.getExamId()); m.put("examName", e.getExamName()); m.put("term", e.getTerm()); m.put("year", e.getAcademicYear());
                        m.put("subjects", new ArrayList<Map<String, Object>>());
                        return m;
                    });
                    @SuppressWarnings("unchecked") List<Map<String, Object>> subs = (List<Map<String, Object>>) ex.get("subjects");
                    subs.add(Map.of("subjectName", r.getSubject().getSubjectName(), "marks", r.getMarksObtained(), "counts", r.getSubject().isCounted()));
                });
        List<Map<String, Object>> bands = new ArrayList<>();
        gradeScaleRepository.findAll().forEach(b -> bands.add(Map.of("label", String.valueOf(b.getGradeLetter()), "min", b.getMinMark() == null ? 0 : b.getMinMark(),
                "max", b.getMaxMark() == null ? 100 : b.getMaxMark(), "remarks", b.getRemarks() == null ? "" : b.getRemarks())));
        return Map.of("exams", new ArrayList<>(byExam.values()), "bands", bands);
    }

    // ── helpers ──
    private Guardian guardian(String token) {
        ParentLogin l = token == null || token.isBlank() ? null : logins.findByToken(token).orElse(null);
        if (l == null || l.getTokenExpires() == null || l.getTokenExpires().isBefore(LocalDateTime.now()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        if (l.getLastSeen() == null || l.getLastSeen().isBefore(LocalDateTime.now().minusHours(1))) { l.setLastSeen(LocalDateTime.now()); logins.save(l); }
        return guardianRepository.findById(l.getGuardianId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again."));
    }
    private List<Student> childrenOf(Guardian g) {
        List<Student> out = new ArrayList<>();
        for (GuardianLink link : linkRepository.findByGuardianGuardianId(g.getGuardianId()))
            studentRepository.findById(link.getStudentId()).filter(Student::isInSchool).ifPresent(out::add);
        return out;
    }
    private void mine(Guardian g, Long studentId) {
        boolean ok = linkRepository.findByGuardianGuardianId(g.getGuardianId()).stream().anyMatch(l -> Objects.equals(l.getStudentId(), studentId));
        if (!ok) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This learner is not linked to your account.");
    }
    private static String sha(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
