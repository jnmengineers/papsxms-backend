package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.Enquiry;
import jnm.engineer.demo.models.FeeStructure;
import jnm.engineer.demo.repositories.EnquiryRepository;
import jnm.engineer.demo.repositories.FeeStructureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The public website: fee structures to show, and admission/contact forms that arrive in the
 * Enquiries inbox. /api/public/** is open to everyone; /api/enquiries/** is staff only (SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
public class PublicSiteController {
    private final FeeStructureRepository feeStructureRepository;
    private final EnquiryRepository enquiryRepository;
    private final jnm.engineer.demo.repositories.StudentRepository studentRepository;
    private final jnm.engineer.demo.repositories.StaffMemberRepository staffRepository;
    private final jnm.engineer.demo.repositories.SchoolClassRepository classRepository;

    /** Live numbers for the website: learners in school, current staff, grade levels. */
    @GetMapping("/api/public/stats")
    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        long learners = studentRepository.findAll().stream().filter(s -> s.isInSchool()).count();
        long staff = staffRepository.findAll().stream().filter(s -> s.getDateLeft() == null).count();
        long grades = classRepository.findAll().stream().map(c -> c.getGradeLevel() != null ? c.getGradeLevel() : c.getClassName())
                .filter(Objects::nonNull).distinct().count();
        return Map.of("learners", learners, "staff", staff, "grades", grades);
    }

    public record EnquiryIn(String kind, String parentName, String phone, String email, String learnerName,
                            String gradeWanted, String message, String website /* honeypot: must stay empty */,
                            java.time.LocalDate preferredInterviewDate) {}
    public record StatusIn(String status, String staffNote, java.time.LocalDate interviewDate) {}

    /** Latest year's fee structures (per section and term) — what the website shows. */
    @GetMapping("/api/public/fees")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> fees() {
        List<FeeStructure> all = feeStructureRepository.findAll();
        String year = all.stream().map(FeeStructure::getYearLabel).filter(Objects::nonNull).max(String::compareTo).orElse(null);
        List<Map<String, Object>> out = new ArrayList<>();
        all.stream().filter(f -> Objects.equals(f.getYearLabel(), year))
                .sorted(Comparator.comparing((FeeStructure f) -> String.valueOf(f.getSection())).thenComparing(f -> f.getTerm() == null ? 0 : f.getTerm()))
                .forEach(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("section", f.getSection()); m.put("year", f.getYearLabel()); m.put("term", f.getTerm());
                    List<Map<String, Object>> items = new ArrayList<>();
                    BigDecimal total = BigDecimal.ZERO;
                    for (var i : f.getItems()) { items.add(Map.of("name", i.getName(), "amount", i.getAmount())); total = total.add(i.getAmount() == null ? BigDecimal.ZERO : i.getAmount()); }
                    m.put("items", items); m.put("total", total);
                    out.add(m);
                });
        return out;
    }

    /** Admission application or contact message from the website. */
    @PostMapping("/api/public/enquiry")
    public Map<String, Object> enquiry(@RequestBody EnquiryIn in) {
        if (in == null) throw bad("Fill in the form.");
        if (in.website() != null && !in.website().isBlank()) return Map.of("message", "Thank you.");   // spam bot
        String phone = in.phone() == null ? "" : in.phone().replaceAll("[^0-9+]", "");
        if (clean(in.parentName(), 100) == null) throw bad("Enter your name.");
        if (phone.length() < 9 || phone.length() > 13) throw bad("Enter a valid phone number.");
        if (enquiryRepository.countByPhoneAndCreatedAtAfter(phone, LocalDateTime.now().minusHours(1)) >= 5) throw bad("Too many messages — please try again later or call the school.");
        Enquiry e = new Enquiry();
        e.setKind("ADMISSION".equalsIgnoreCase(in.kind()) ? "ADMISSION" : "CONTACT");
        e.setParentName(clean(in.parentName(), 100)); e.setPhone(phone); e.setEmail(clean(in.email(), 100));
        e.setLearnerName(clean(in.learnerName(), 100)); e.setGradeWanted(clean(in.gradeWanted(), 40));
        e.setMessage(clean(in.message(), 1500)); e.setStatus("NEW"); e.setCreatedAt(LocalDateTime.now());
        if ("ADMISSION".equals(e.getKind()) && in.preferredInterviewDate() != null) {
            if (in.preferredInterviewDate().isBefore(java.time.LocalDate.now())) throw bad("The preferred interview date can't be in the past.");
            e.setPreferredInterviewDate(in.preferredInterviewDate());
        }
        enquiryRepository.save(e);
        return Map.of("message", "ADMISSION".equals(e.getKind())
                ? "Thank you — your application has been received. The school will call you."
                : "Thank you — your message has been received.");
    }

    // ── Staff inbox ──
    @GetMapping("/api/enquiries")
    public List<Enquiry> list() { return enquiryRepository.findAllByOrderByCreatedAtDesc(); }

    @PutMapping("/api/enquiries/{id}")
    public Enquiry update(@PathVariable Long id, @RequestBody StatusIn in, @AuthenticationPrincipal UserDetails me) {
        Enquiry e = enquiryRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found."));
        if (in.status() != null && Set.of("NEW", "CONTACTED", "INTERVIEW", "ADMITTED", "NOT_ADMITTED", "CLOSED").contains(in.status())) e.setStatus(in.status());
        if (in.interviewDate() != null) e.setInterviewDate(in.interviewDate());
        if (in.staffNote() != null) e.setStaffNote(clean(in.staffNote(), 300));
        e.setUpdatedAt(LocalDateTime.now()); e.setHandledBy(me != null ? me.getUsername() : null);
        return enquiryRepository.save(e);
    }

    private static String clean(String v, int max) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
