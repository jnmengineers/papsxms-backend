package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.JobApplication;
import jnm.engineer.demo.models.JobApplicationDoc;
import jnm.engineer.demo.models.SitePost;
import jnm.engineer.demo.repositories.JobApplicationRepository;
import jnm.engineer.demo.repositories.SitePostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Job applications from the website's Careers section. Vacancies themselves are SitePosts of kind JOB
 * (title, tag = Teaching / Non-teaching, eventDate = closing date). Public: /api/public/job-apply.
 * Staff (ADMIN, HEAD_TEACHER — SecurityConfig): /api/job-applications.
 */
@RestController
@RequiredArgsConstructor
public class JobApplicationController {
    private static final Set<String> STATUSES = Set.of("NEW", "SHORTLISTED", "INTERVIEW", "HIRED", "NOT_SUCCESSFUL");
    private final JobApplicationRepository repo;
    private final SitePostRepository postRepo;
    private final jnm.engineer.demo.repositories.JobApplicationDocRepository docRepo;

    public record ApplyIn(Long vacancyId, String fullName, String phone, String email, String qualifications, String message,
                          String cvBase64, String cvName, String website /* honeypot */, List<DocIn> docs) {}
    public record DocIn(String name, String base64) {}
    public record StatusIn(String status, String staffNote) {}

    @PostMapping("/api/public/job-apply")
    @Transactional
    public Map<String, String> apply(@RequestBody ApplyIn in) {
        if (in == null) throw bad("Fill in the form.");
        if (in.website() != null && !in.website().isBlank()) return Map.of("message", "Thank you.");
        SitePost v = in.vacancyId() == null ? null : postRepo.findById(in.vacancyId()).orElse(null);
        if (v == null || !"JOB".equals(v.getKind()) || !Boolean.TRUE.equals(v.getPublished())) throw bad("This vacancy is no longer open.");
        if (v.getEventDate() != null && v.getEventDate().isBefore(LocalDate.now())) throw bad("Applications for this vacancy have closed.");
        String phone = in.phone() == null ? "" : in.phone().replaceAll("[^0-9+]", "");
        if (cut(in.fullName(), 100) == null) throw bad("Enter your full name.");
        if (phone.length() < 9 || phone.length() > 13) throw bad("Enter a valid phone number.");
        if (repo.countByPhoneAndCreatedAtAfter(phone, LocalDateTime.now().minusHours(1)) >= 3) throw bad("Too many applications — please try again later.");
        JobApplication a = new JobApplication();
        a.setVacancyId(v.getPostId()); a.setVacancyTitle(v.getTitle());
        a.setFullName(cut(in.fullName(), 100)); a.setPhone(phone); a.setEmail(cut(in.email(), 100));
        a.setQualifications(cut(in.qualifications(), 300)); a.setMessage(cut(in.message(), 1500));
        if (in.cvBase64() != null && !in.cvBase64().isBlank()) {
            a.setCvData(pdf(in.cvBase64(), "The CV", 3_000_000)); a.setCvName(cut(in.cvName(), 120));
        }
        // Professional documents: up to 5 PDFs; CV + documents together at most 6 MB
        List<DocIn> docs = in.docs() == null ? List.of() : in.docs();
        if (docs.size() > 5) throw bad("Attach at most 5 documents.");
        List<JobApplicationDoc> files = new ArrayList<>();
        long total = a.isHasCv() ? a.getCvData().length : 0;
        for (DocIn d : docs) {
            if (d == null || d.base64() == null || d.base64().isBlank()) continue;
            byte[] data = pdf(d.base64(), "A document", 3_000_000);
            total += data.length;
            if (total > 6_000_000) throw bad("The CV and documents together are too large (max 6 MB).");
            JobApplicationDoc f = new JobApplicationDoc(); f.setFileName(cut(d.name(), 120)); f.setData(data); files.add(f);
        }
        a.setStatus("NEW"); a.setCreatedAt(LocalDateTime.now());
        repo.save(a);
        for (JobApplicationDoc f : files) { f.setApplicationId(a.getApplicationId()); docRepo.save(f); }
        return Map.of("message", "Thank you — your application has been received. Shortlisted candidates will be contacted.");
    }

    @GetMapping("/api/job-applications")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JobApplication a : repo.findAllByOrderByCreatedAtDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("applicationId", a.getApplicationId()); m.put("vacancyId", a.getVacancyId()); m.put("vacancyTitle", a.getVacancyTitle());
            m.put("fullName", a.getFullName()); m.put("phone", a.getPhone()); m.put("email", a.getEmail());
            m.put("qualifications", a.getQualifications()); m.put("message", a.getMessage()); m.put("hasCv", a.isHasCv());
            m.put("status", a.getStatus()); m.put("staffNote", a.getStaffNote()); m.put("createdAt", a.getCreatedAt()); m.put("handledBy", a.getHandledBy());
            m.put("docs", docRepo.findByApplicationIdOrderByDocIdAsc(a.getApplicationId()).stream()
                    .map(d -> Map.<String, Object>of("docId", d.getDocId(), "fileName", d.getFileName() == null ? "Document" : d.getFileName())).toList());
            out.add(m);
        }
        return out;
    }

    @GetMapping("/api/job-applications/docs/{docId}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> doc(@PathVariable Long docId) {
        JobApplicationDoc d = docRepo.findById(docId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found."));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(d.getData());
    }

    @GetMapping("/api/job-applications/{id}/cv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> cv(@PathVariable Long id) {
        JobApplication a = repo.findById(id).filter(JobApplication::isHasCv).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No CV."));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"CV-" + a.getFullName().replaceAll("[^A-Za-z0-9]+", "-") + ".pdf\"").body(a.getCvData());
    }

    @PutMapping("/api/job-applications/{id}")
    public JobApplication update(@PathVariable Long id, @RequestBody StatusIn in, @AuthenticationPrincipal UserDetails me) {
        JobApplication a = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found."));
        if (in.status() != null && STATUSES.contains(in.status())) a.setStatus(in.status());
        if (in.staffNote() != null) a.setStaffNote(cut(in.staffNote(), 300));
        a.setUpdatedAt(LocalDateTime.now()); a.setHandledBy(me != null ? me.getUsername() : null);
        return repo.save(a);
    }

    @DeleteMapping("/api/job-applications/{id}")
    @Transactional
    public Map<String, String> delete(@PathVariable Long id) { docRepo.deleteByApplicationId(id); repo.deleteById(id); return Map.of("message", "Deleted."); }

    private static byte[] pdf(String base64, String what, int max) {
        String b64 = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
        byte[] data;
        try { data = Base64.getDecoder().decode(b64); } catch (IllegalArgumentException e) { throw bad(what + " could not be read."); }
        if (data.length > max) throw bad(what + " is too large.");
        if (data.length < 5 || !new String(data, 0, 5).startsWith("%PDF")) throw bad(what + " must be a PDF file.");
        return data;
    }

    private static String cut(String v, int max) { if (v == null || v.isBlank()) return null; String t = v.trim(); return t.length() > max ? t.substring(0, max) : t; }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
