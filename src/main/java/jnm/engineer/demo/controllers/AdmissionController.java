package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.*;
import jnm.engineer.demo.repositories.*;
import jnm.engineer.demo.services.GuardianService;
import jnm.engineer.demo.services.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Admitting a learner — at the office (walk-in) or from a website application. One step:
 * learner (next admission number), class, parent (matched by phone, so siblings share one), and the
 * application marked as admitted. ADMIN and CLERK (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admissions")
@RequiredArgsConstructor
public class AdmissionController {
    private static final Pattern ADM = Pattern.compile("^ADM(\\d+)$", Pattern.CASE_INSENSITIVE);
    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final GuardianRepository guardianRepository;
    private final GuardianLinkRepository linkRepository;
    private final EnquiryRepository enquiryRepository;

    public record AdmitIn(String admissionNumber, String firstName, String lastName, LocalDate dateOfBirth, String gender, Long classId,
                          String parentName, String parentPhone, String relationship, String parentEmail, Long enquiryId) {}

    /** The next free admission number, e.g. ADM2341 (highest ADM number + 1). */
    @GetMapping("/next-number")
    public Map<String, String> nextNumber() {
        int max = 0;
        for (Student s : studentRepository.findAll()) {
            Matcher m = s.getAdmissionNumber() == null ? null : ADM.matcher(s.getAdmissionNumber().trim());
            if (m != null && m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return Map.of("admissionNumber", "ADM" + (max + 1));
    }

    @PostMapping
    @Transactional
    public Map<String, Object> admit(@RequestBody AdmitIn in, @AuthenticationPrincipal UserDetails me) {
        String adm = in.admissionNumber() == null || in.admissionNumber().isBlank() ? nextNumber().get("admissionNumber") : in.admissionNumber().trim().toUpperCase();
        if (blank(in.firstName()) || blank(in.lastName())) throw bad("Enter the learner's first and last name.");
        if (in.classId() == null) throw bad("Choose the class.");
        if (in.dateOfBirth() != null && in.dateOfBirth().isAfter(LocalDate.now())) throw bad("Date of birth can't be in the future.");
        if (studentRepository.findAll().stream().anyMatch(s -> adm.equalsIgnoreCase(String.valueOf(s.getAdmissionNumber()).trim())))
            throw bad("Admission number " + adm + " is already used.");

        Student s = new Student();
        s.setAdmissionNumber(adm);
        s.setFirstName(in.firstName().trim().toUpperCase());
        s.setLastName(in.lastName().trim().toUpperCase());
        s.setDateOfBirth(in.dateOfBirth());
        s.setGender(blank(in.gender()) ? null : in.gender().trim());
        Student saved = studentService.create(s, in.classId());

        String parentMsg = "";
        if (!blank(in.parentPhone())) {
            String phone = GuardianService.phone(in.parentPhone());
            Guardian g = guardianRepository.findByPhone(phone).orElse(null);
            if (g == null) {
                if (blank(in.parentName())) throw bad("Enter the parent's name (or leave the phone empty).");
                g = new Guardian();
                g.setFullName(in.parentName().trim().toUpperCase());
                g.setPhone(phone);
                g.setRelationship(blank(in.relationship()) ? null : in.relationship().trim());
                g.setEmail(blank(in.parentEmail()) ? null : in.parentEmail().trim());
                g = guardianRepository.save(g);
                parentMsg = " Parent " + g.getFullName() + " added.";
            } else parentMsg = " Linked to existing parent " + g.getFullName() + " (same phone).";
            GuardianLink l = new GuardianLink();
            l.setGuardian(g); l.setStudentId(saved.getStudentId());
            linkRepository.save(l);
        }

        if (in.enquiryId() != null) enquiryRepository.findById(in.enquiryId()).ifPresent(e -> {
            e.setStatus("ADMITTED");
            e.setAdmittedAs(adm);
            e.setStaffNote("Admitted as " + adm);
            e.setUpdatedAt(LocalDateTime.now());
            e.setHandledBy(me != null ? me.getUsername() : null);
            enquiryRepository.save(e);
        });
        return Map.of("studentId", saved.getStudentId(), "admissionNumber", adm,
                "message", saved.getFirstName() + " " + saved.getLastName() + " admitted as " + adm + "." + parentMsg);
    }

    private static boolean blank(String v) { return v == null || v.isBlank(); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
