package jnm.engineer.demo.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jnm.engineer.demo.models.*;
import jnm.engineer.demo.repositories.*;
import jnm.engineer.demo.services.GuardianService;
import jnm.engineer.demo.services.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Forms built inside the system (parents' feedback, surveys, registrations…).
 * Staff (ADMIN, CLERK, HEAD_TEACHER — SecurityConfig): /api/surveys.  Public: /api/public/surveys/{code}.
 */
@RestController
@RequiredArgsConstructor
public class SurveyController {
    private static final Set<String> TYPES = Set.of("short", "paragraph", "single", "multi", "dropdown", "rating", "scale", "yesno", "number", "date", "heading");
    private static final List<String> YESNO = List.of("Yes", "No");
    private final SurveyRepository repo;
    private final SurveyResponseRepository responses;
    private final GuardianRepository guardianRepository;
    private final GuardianLinkRepository linkRepository;
    private final StudentRepository studentRepository;
    private final SmsService smsService;
    private final ObjectMapper json = new ObjectMapper();
    private final SecureRandom rnd = new SecureRandom();

    public record SurveyIn(String title, String description, List<Map<String, Object>> questions, String status, LocalDate opensOn, LocalDate closesOn,
                           Boolean allowAnonymous, Boolean collectContact, Boolean onePerPhone, String thankYou, Boolean showOnWebsite) {}
    public record AnswerIn(String respondentName, String phone, Boolean anonymous, Map<String, Object> answers, String website /* honeypot */) {}
    public record SmsIn(String section, String message) {}

    // ══ Staff ══════════════════════════════════════════════
    @GetMapping("/api/surveys")
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Survey s : repo.findAllByOrderByCreatedAtDesc()) { Map<String, Object> m = view(s, false); m.put("responses", responses.countBySurveyId(s.getId())); out.add(m); }
        return out;
    }

    @GetMapping("/api/surveys/{id}")
    public Map<String, Object> get(@PathVariable Long id) { return view(find(id), true); }

    @PostMapping("/api/surveys")
    public Map<String, Object> create(@RequestBody SurveyIn in, @AuthenticationPrincipal UserDetails me) {
        Survey s = new Survey();
        s.setCode(newCode()); s.setCreatedBy(me != null ? me.getUsername() : null); s.setCreatedAt(LocalDateTime.now());
        return view(repo.save(apply(s, in)), true);
    }

    @PutMapping("/api/surveys/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody SurveyIn in) { return view(repo.save(apply(find(id), in)), true); }

    @PostMapping("/api/surveys/{id}/duplicate")
    public Map<String, Object> duplicate(@PathVariable Long id, @AuthenticationPrincipal UserDetails me) {
        Survey src = find(id), s = new Survey();
        s.setCode(newCode()); s.setTitle("Copy of " + src.getTitle()); s.setDescription(src.getDescription()); s.setQuestions(src.getQuestions());
        s.setStatus("DRAFT"); s.setAllowAnonymous(src.getAllowAnonymous()); s.setCollectContact(src.getCollectContact()); s.setOnePerPhone(src.getOnePerPhone());
        s.setThankYou(src.getThankYou()); s.setCreatedBy(me != null ? me.getUsername() : null); s.setCreatedAt(LocalDateTime.now()); s.setUpdatedAt(LocalDateTime.now());
        return view(repo.save(s), true);
    }

    @DeleteMapping("/api/surveys/{id}")
    @Transactional
    public Map<String, String> delete(@PathVariable Long id) { find(id); responses.deleteBySurveyId(id); repo.deleteById(id); return Map.of("message", "Form deleted."); }

    @GetMapping("/api/surveys/{id}/responses")
    public List<Map<String, Object>> responses(@PathVariable Long id) throws Exception {
        find(id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (SurveyResponse r : responses.findBySurveyIdOrderByCreatedAtDesc(id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId()); m.put("createdAt", r.getCreatedAt()); m.put("respondentName", r.getRespondentName()); m.put("phone", r.getPhone());
            m.put("answers", r.getAnswers() == null ? Map.of() : json.readValue(r.getAnswers(), new TypeReference<Map<String, Object>>() {}));
            out.add(m);
        }
        return out;
    }

    @DeleteMapping("/api/surveys/{id}/responses/{rid}")
    public Map<String, String> deleteResponse(@PathVariable Long id, @PathVariable Long rid) { responses.deleteById(rid); return Map.of("message", "Response deleted."); }

    /** SMS the form link to parents: all, or one section (PRE_SCHOOL, LOWER_PRIMARY, UPPER_PRIMARY, JUNIOR_SCHOOL). */
    @PostMapping("/api/surveys/{id}/sms")
    @Transactional(readOnly = true)
    public Map<String, Object> sms(@PathVariable Long id, @RequestBody SmsIn in, @AuthenticationPrincipal UserDetails me) {
        Survey s = find(id);
        if (!"OPEN".equals(s.getStatus())) throw bad("Open the form before sending it to parents.");
        String text = in == null || in.message() == null ? "" : in.message().trim();
        if (text.isEmpty()) throw bad("Write the message.");
        if (text.length() > 459) throw bad("The message is too long (max 3 SMS, 459 characters).");
        String section = in.section() == null || in.section().isBlank() ? "ALL" : in.section();
        Map<Long, Student> students = new HashMap<>();
        studentRepository.findAll().forEach(st -> { if (st.isInSchool()) students.put(st.getStudentId(), st); });
        Map<String, SmsService.Outgoing> byPhone = new LinkedHashMap<>();
        for (Guardian g : guardianRepository.findAll()) {
            String phone = GuardianService.phone(g.getPhone());
            if (phone == null || byPhone.containsKey(phone)) continue;
            boolean match = linkRepository.findByGuardianGuardianId(g.getGuardianId()).stream().anyMatch(l -> {
                Student st = students.get(l.getStudentId());
                if (st == null) return false;
                if ("ALL".equals(section)) return true;
                SchoolClass c = st.getSchoolClass();
                return c != null && section.equals(String.valueOf(c.getSection()));
            });
            if (match) byPhone.put(phone, new SmsService.Outgoing(phone, text, g.getGuardianId()));
        }
        if (byPhone.isEmpty()) throw bad("No parents with phone numbers found for that choice.");
        List<SmsService.Result> results = smsService.send(new ArrayList<>(byPhone.values()), me != null ? me.getUsername() : "forms");
        long ok = results.stream().filter(SmsService.Result::ok).count();
        return Map.of("sent", ok, "failed", results.size() - ok, "message", ok + " SMS sent" + (results.size() > ok ? ", " + (results.size() - ok) + " failed." : "."));
    }

    // ══ Public ═════════════════════════════════════════════
    /** Open forms marked "Show on the school website" — for the website's Forms & surveys section. */
    @GetMapping("/api/public/surveys")
    public List<Map<String, Object>> websiteForms() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Survey s : repo.findAllByOrderByCreatedAtDesc()) {
            if (!Boolean.TRUE.equals(s.getShowOnWebsite()) || closedReason(s) != null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", s.getCode()); m.put("title", s.getTitle()); m.put("closesOn", s.getClosesOn());
            String d = s.getDescription() == null ? "" : s.getDescription().replaceAll("\\s+", " ").trim();
            m.put("summary", d.length() > 180 ? d.substring(0, 177) + "…" : d);
            out.add(m);
        }
        return out;
    }

    @GetMapping("/api/public/surveys/{code}")
    public Map<String, Object> publicForm(@PathVariable String code) {
        Survey s = repo.findByCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This form does not exist."));
        String closed = closedReason(s);
        Map<String, Object> m = view(s, true);
        m.remove("createdBy"); m.remove("id");
        if (closed != null) { m.put("closed", closed); m.remove("questions"); }
        return m;
    }

    @PostMapping("/api/public/surveys/{code}/responses")
    public Map<String, String> submit(@PathVariable String code, @RequestBody AnswerIn in) throws Exception {
        Survey s = repo.findByCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This form does not exist."));
        String closed = closedReason(s);
        if (closed != null) throw bad(closed);
        if (in == null) throw bad("Fill in the form.");
        if (in.website() != null && !in.website().isBlank()) return Map.of("message", "Thank you.");
        boolean anon = Boolean.TRUE.equals(s.getAllowAnonymous()) && Boolean.TRUE.equals(in.anonymous());
        String phone = anon || in.phone() == null ? "" : in.phone().replaceAll("[^0-9+]", "");
        if (!phone.isEmpty() && (phone.length() < 9 || phone.length() > 13)) throw bad("Enter a valid phone number.");
        if (Boolean.TRUE.equals(s.getOnePerPhone()) && !anon) {
            if (phone.isEmpty()) throw bad("Enter your phone number.");
            if (responses.existsBySurveyIdAndPhone(s.getId(), phone)) throw bad("A response from this phone number has already been received. Thank you!");
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        Map<String, Object> given = in.answers() == null ? Map.of() : in.answers();
        for (Map<String, Object> q : questionsOf(s)) {
            String type = String.valueOf(q.get("type")), id = String.valueOf(q.get("id")), label = String.valueOf(q.getOrDefault("label", "a question"));
            if ("heading".equals(type)) continue;
            Object v = given.get(id);
            Object ok = check(type, q, v);
            if (ok == null) { if (Boolean.TRUE.equals(q.get("required"))) throw bad("Please answer: " + label); continue; }
            clean.put(id, ok);
        }
        if (clean.isEmpty()) throw bad("Please answer at least one question.");
        SurveyResponse r = new SurveyResponse();
        r.setSurveyId(s.getId()); r.setCreatedAt(LocalDateTime.now()); r.setAnswers(json.writeValueAsString(clean));
        if (!anon) { r.setRespondentName(cut(in.respondentName(), 100)); r.setPhone(phone.isEmpty() ? null : phone); }
        responses.save(r);
        return Map.of("message", s.getThankYou() == null || s.getThankYou().isBlank() ? "Thank you! Your response has been received." : s.getThankYou());
    }

    // ══ Helpers ════════════════════════════════════════════
    /** The cleaned answer, or null when empty/invalid. */
    @SuppressWarnings("unchecked")
    private Object check(String type, Map<String, Object> q, Object v) {
        if (v == null) return null;
        List<String> opts = q.get("options") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
        switch (type) {
            case "short": case "paragraph": { String t = String.valueOf(v).trim(); if (t.isEmpty()) return null; int max = "short".equals(type) ? 300 : 3000; return t.length() > max ? t.substring(0, max) : t; }
            case "single": case "dropdown": case "rating": { String t = String.valueOf(v); return opts.contains(t) ? t : null; }
            case "yesno": { String t = String.valueOf(v); return YESNO.contains(t) ? t : null; }
            case "multi": { if (!(v instanceof List<?> l)) return null; List<String> picked = l.stream().map(String::valueOf).filter(opts::contains).distinct().toList(); return picked.isEmpty() ? null : picked; }
            case "scale": { try { int n = (int) Math.round(Double.parseDouble(String.valueOf(v))); return n >= 1 && n <= 5 ? n : null; } catch (Exception e) { return null; } }
            case "number": { try { double d = Double.parseDouble(String.valueOf(v).trim()); return Double.isFinite(d) ? d : null; } catch (Exception e) { return null; } }
            case "date": { try { return LocalDate.parse(String.valueOf(v)).toString(); } catch (Exception e) { return null; } }
            default: return null;
        }
    }

    private Survey apply(Survey s, SurveyIn in) {
        if (in == null || in.title() == null || in.title().isBlank()) throw bad("Give the form a title.");
        List<Map<String, Object>> qs = in.questions() == null ? List.of() : in.questions();
        if (qs.size() > 100) throw bad("A form can have at most 100 questions.");
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> q : qs) {
            String type = String.valueOf(q.get("type")), id = String.valueOf(q.get("id"));
            if (!TYPES.contains(type)) throw bad("Unknown question type: " + type);
            if (id.isBlank() || "null".equals(id) || !ids.add(id)) throw bad("Every question needs its own id.");
            if (String.valueOf(q.getOrDefault("label", "")).isBlank()) throw bad("A question has no wording.");
            if (Set.of("single", "multi", "dropdown", "rating").contains(type) && !(q.get("options") instanceof List<?> l && l.size() >= 2)) throw bad("\"" + q.get("label") + "\" needs at least two choices.");
        }
        String status = in.status() == null ? "DRAFT" : in.status().toUpperCase();
        if (!Set.of("DRAFT", "OPEN", "CLOSED").contains(status)) status = "DRAFT";
        if ("OPEN".equals(status) && qs.stream().noneMatch(q -> !"heading".equals(q.get("type")))) throw bad("Add at least one question before opening the form.");
        try { s.setQuestions(json.writeValueAsString(qs)); } catch (Exception e) { throw bad("The questions could not be saved."); }
        s.setTitle(cut(in.title(), 200)); s.setDescription(in.description() == null ? null : in.description().trim());
        s.setStatus(status); s.setOpensOn(in.opensOn()); s.setClosesOn(in.closesOn());
        s.setAllowAnonymous(Boolean.TRUE.equals(in.allowAnonymous())); s.setCollectContact(Boolean.TRUE.equals(in.collectContact())); s.setOnePerPhone(Boolean.TRUE.equals(in.onePerPhone()));
        s.setThankYou(cut(in.thankYou(), 600)); s.setShowOnWebsite(Boolean.TRUE.equals(in.showOnWebsite())); s.setUpdatedAt(LocalDateTime.now());
        return s;
    }

    private String closedReason(Survey s) {
        LocalDate today = LocalDate.now();
        if (!"OPEN".equals(s.getStatus())) return "This form is not accepting responses.";
        if (s.getOpensOn() != null && today.isBefore(s.getOpensOn())) return "This form opens on " + s.getOpensOn() + ".";
        if (s.getClosesOn() != null && today.isAfter(s.getClosesOn())) return "This form closed on " + s.getClosesOn() + ".";
        return null;
    }

    private List<Map<String, Object>> questionsOf(Survey s) {
        try { return s.getQuestions() == null ? List.of() : json.readValue(s.getQuestions(), new TypeReference<List<Map<String, Object>>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private Map<String, Object> view(Survey s, boolean withQuestions) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId()); m.put("code", s.getCode()); m.put("title", s.getTitle()); m.put("description", s.getDescription());
        m.put("status", s.getStatus()); m.put("opensOn", s.getOpensOn()); m.put("closesOn", s.getClosesOn());
        m.put("allowAnonymous", Boolean.TRUE.equals(s.getAllowAnonymous())); m.put("collectContact", Boolean.TRUE.equals(s.getCollectContact()));
        m.put("onePerPhone", Boolean.TRUE.equals(s.getOnePerPhone())); m.put("thankYou", s.getThankYou()); m.put("showOnWebsite", Boolean.TRUE.equals(s.getShowOnWebsite()));
        m.put("createdBy", s.getCreatedBy()); m.put("createdAt", s.getCreatedAt()); m.put("updatedAt", s.getUpdatedAt());
        if (withQuestions) m.put("questions", questionsOf(s));
        return m;
    }

    private Survey find(Long id) { return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Form not found.")); }
    private String newCode() {
        String chars = "abcdefghjkmnpqrstuvwxyz23456789";
        for (int t = 0; t < 20; t++) { StringBuilder b = new StringBuilder(); for (int i = 0; i < 6; i++) b.append(chars.charAt(rnd.nextInt(chars.length()))); if (!repo.existsByCode(b.toString())) return b.toString(); }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create a link code.");
    }
    private static String cut(String v, int max) { if (v == null || v.isBlank()) return null; String t = v.trim(); return t.length() > max ? t.substring(0, max) : t; }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
