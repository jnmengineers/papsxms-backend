package jnm.engineer.demo.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jnm.engineer.demo.models.ParentFeedback;
import jnm.engineer.demo.repositories.ParentFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/** Parents' feedback: public form (/api/public/feedback) and staff results (/api/feedback). */
@RestController
@RequiredArgsConstructor
public class ParentFeedbackController {
    // The school's end-of-year form: answer keys and the choices each one accepts
    private static final List<String> RATE = List.of("Excellent", "Good", "Fair", "Needs improvement");
    private static final Map<String, List<String>> CHOICES = new LinkedHashMap<>();
    static {
        for (int i = 1; i <= 8; i++) CHOICES.put("b" + i, RATE);
        CHOICES.put("overall", List.of("Very satisfied", "Satisfied", "Neutral", "Dissatisfied", "Very dissatisfied"));
        CHOICES.put("recommend", List.of("Yes", "Not sure", "No"));
        CHOICES.put("band", List.of("Pre-Primary (PP1/PP2)", "Lower Primary (Grades 1–3)", "Upper Primary (Grades 4–6)", "Junior School (Grades 7–9)"));
    }
    private static final Set<String> TEXTS = Set.of("c1", "c2", "c3", "c4", "c5", "learnerName");
    private final ParentFeedbackRepository repo;
    private final ObjectMapper json = new ObjectMapper();

    public record FeedbackIn(String parentName, String phone, String className, Map<String, Object> answers,
                             String doingWell, String improve, String website /* honeypot */) {}

    @PostMapping("/api/public/feedback")
    public Map<String, String> submit(@RequestBody FeedbackIn in) throws Exception {
        if (in == null) throw bad("Fill in the form.");
        if (in.website() != null && !in.website().isBlank()) return Map.of("message", "Thank you.");
        Map<String, Object> clean = new LinkedHashMap<>();
        if (in.answers() != null) in.answers().forEach((k, v) -> {
            String val = v == null ? "" : String.valueOf(v).trim();
            if (val.isEmpty()) return;
            if (CHOICES.containsKey(k) && CHOICES.get(k).contains(val)) clean.put(k, val);
            else if (TEXTS.contains(k)) clean.put(k, val.length() > 1500 ? val.substring(0, 1500) : val);
        });
        if (clean.keySet().stream().noneMatch(k -> k.startsWith("b") || k.startsWith("c") || k.equals("overall") || k.equals("recommend")))
            throw bad("Please answer at least one question.");
        String phone = in.phone() == null ? "" : in.phone().replaceAll("[^0-9+]", "");
        if (!phone.isEmpty() && repo.countByPhoneAndCreatedAtAfter(phone, LocalDateTime.now().minusHours(1)) >= 3)
            throw bad("Thank you — we already received your feedback.");
        ParentFeedback f = new ParentFeedback();
        f.setCreatedAt(LocalDateTime.now());
        f.setParentName(cut(in.parentName(), 100)); f.setPhone(phone.isEmpty() ? null : cut(phone, 20)); f.setClassName(cut(in.className(), 60));
        f.setAnswers(json.writeValueAsString(clean));
        repo.save(f);
        return Map.of("message", "Thank you for taking the time to share your views. Your feedback will guide our continuous improvement.");
    }

    @GetMapping("/api/feedback")
    public List<Map<String, Object>> list() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ParentFeedback f : repo.findAllByOrderByCreatedAtDesc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId()); m.put("createdAt", f.getCreatedAt()); m.put("parentName", f.getParentName()); m.put("phone", f.getPhone());
            m.put("className", f.getClassName()); m.put("doingWell", f.getDoingWell()); m.put("improve", f.getImprove());
            m.put("answers", f.getAnswers() == null ? Map.of() : json.readValue(f.getAnswers(), Map.class));
            out.add(m);
        }
        return out;
    }

    @DeleteMapping("/api/feedback/{id}")
    public Map<String, String> delete(@PathVariable Long id) { repo.deleteById(id); return Map.of("message", "Deleted."); }

    private static int parseInt(Object v) { try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; } }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String cut(String v, int max) { if (v == null || v.isBlank()) return null; String t = v.trim(); return t.length() > max ? t.substring(0, max) : t; }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
