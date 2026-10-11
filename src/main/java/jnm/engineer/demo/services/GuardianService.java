package jnm.engineer.demo.services;

import jnm.engineer.demo.models.Guardian;
import jnm.engineer.demo.models.GuardianLink;
import jnm.engineer.demo.models.Student;
import jnm.engineer.demo.repositories.GuardianLinkRepository;
import jnm.engineer.demo.repositories.GuardianRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Parents / guardians and their children. Children who share a guardian are siblings. */
@Service
@RequiredArgsConstructor
public class GuardianService {
    private final GuardianRepository guardianRepository;
    private final GuardianLinkRepository linkRepository;
    private final StudentService studentService;

    public record GuardianIn(Long guardianId, String fullName, String phone, String phone2, String email, String relationship, String notes, List<Long> studentIds) {}

    /** 0712 345678 / 712345678 / 254712345678 / +254 712 345678 → +254712345678. Other countries: +<8–15 digits>. */
    public static String phone(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim();
        boolean plus = t.startsWith("+");
        String d = t.replaceAll("\\D", "");
        if (!plus && d.length() == 10 && d.startsWith("0") && (d.charAt(1) == '7' || d.charAt(1) == '1')) return "+254" + d.substring(1);
        if (!plus && d.length() == 9 && (d.startsWith("7") || d.startsWith("1"))) return "+254" + d;
        if (d.length() == 12 && d.startsWith("254")) return "+" + d;
        if (plus && d.length() >= 8 && d.length() <= 15) return "+" + d;
        throw bad("Check the phone number " + raw + " (e.g. 0712 345678).");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        Map<Long, Student> students = students();
        Map<Long, List<GuardianLink>> byGuardian = new HashMap<>();
        linkRepository.findAll().forEach(l -> byGuardian.computeIfAbsent(l.getGuardian().getGuardianId(), k -> new ArrayList<>()).add(l));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Guardian g : guardianRepository.findAllByOrderByFullNameAsc()) out.add(map(g, byGuardian.getOrDefault(g.getGuardianId(), List.of()), students));
        return out;
    }

    @Transactional
    public Map<String, Object> save(GuardianIn in) {
        Guardian g = in.guardianId() == null ? new Guardian() : guardianRepository.findById(in.guardianId()).orElseThrow(() -> notFound("Parent not found."));
        String name = trim(in.fullName(), 100);
        if (name == null || name.length() < 3) throw bad("Enter the parent's full name.");
        String p1 = phone(in.phone());
        if (p1 == null) throw bad("Enter the parent's phone number.");
        String p2 = phone(in.phone2());
        guardianRepository.findByPhone(p1).filter(o -> !o.getGuardianId().equals(g.getGuardianId())).ifPresent(o -> {
            throw bad("The phone " + p1 + " is already recorded for " + o.getFullName() + " — add the children to that parent instead.");
        });
        g.setFullName(name.toUpperCase());
        g.setPhone(p1);
        g.setPhone2(p2);
        g.setEmail(trim(in.email(), 100));
        g.setRelationship(trim(in.relationship(), 30));
        g.setNotes(trim(in.notes(), 200));
        guardianRepository.save(g);
        if (in.studentIds() != null) {
            Set<Long> want = new LinkedHashSet<>(in.studentIds());
            Map<Long, Student> students = students();
            for (Long id : want) if (!students.containsKey(id)) throw bad("A learner was not found — refresh the page.");
            for (GuardianLink l : linkRepository.findByGuardianGuardianId(g.getGuardianId())) if (!want.remove(l.getStudentId())) linkRepository.delete(l);
            for (Long id : want) { GuardianLink l = new GuardianLink(); l.setGuardian(g); l.setStudentId(id); linkRepository.save(l); }
        }
        return Map.of("message", g.getFullName() + " saved.", "guardianId", g.getGuardianId());
    }

    @Transactional
    public Map<String, Object> delete(Long id) {
        Guardian g = guardianRepository.findById(id).orElseThrow(() -> notFound("Parent not found."));
        linkRepository.deleteAll(linkRepository.findByGuardianGuardianId(id));
        guardianRepository.delete(g);
        return Map.of("message", g.getFullName() + " removed.");
    }

    /** A learner's parents and siblings (other children of the same parents). */
    @Transactional(readOnly = true)
    public Map<String, Object> forStudent(Long studentId) {
        Map<Long, Student> students = students();
        List<Map<String, Object>> parents = new ArrayList<>();
        Map<Long, Map<String, Object>> siblings = new LinkedHashMap<>();
        for (GuardianLink mine : linkRepository.findByStudentId(studentId)) {
            List<GuardianLink> all = linkRepository.findByGuardianGuardianId(mine.getGuardian().getGuardianId());
            parents.add(map(mine.getGuardian(), all, students));
            for (GuardianLink l : all) {
                Student s = students.get(l.getStudentId());
                if (s != null && !l.getStudentId().equals(studentId)) siblings.putIfAbsent(l.getStudentId(), child(s));
            }
        }
        return Map.of("parents", parents, "siblings", new ArrayList<>(siblings.values()));
    }

    // ══ Import from Excel / CSV ═════════════════════════════════════════════
    static final List<String> TEMPLATE_HEADER = List.of("Admission No", "Learner", "Class", "Parent / guardian name", "Phone", "Relationship", "Other phone", "Email");

    /** An Excel template listing every learner who has no parent yet — fill in the parent columns and upload. */
    @Transactional(readOnly = true)
    public byte[] template() {
        Set<Long> linked = new HashSet<>();
        linkRepository.findAll().forEach(l -> linked.add(l.getStudentId()));
        List<Student> todo = new ArrayList<>(studentService.getAllStudents().stream().filter(s -> !linked.contains(s.getStudentId())).toList());
        todo.sort(Comparator.comparing((Student s) -> s.getSchoolClass() == null ? "~" : String.valueOf(s.getSchoolClass().getClassName()))
                .thenComparing(s -> (s.getFirstName() + " " + s.getLastName())));
        List<List<String>> rows = new ArrayList<>();
        rows.add(TEMPLATE_HEADER);
        for (Student s : todo) rows.add(Arrays.asList(s.getAdmissionNumber(), (s.getFirstName() + " " + s.getLastName()).trim(),
                s.getSchoolClass() != null ? s.getSchoolClass().getClassName() : "", "", "", "", "", ""));
        try { return XlsxWriter.write("Parents", rows, new double[]{14, 28, 16, 30, 16, 14, 16, 26}); }
        catch (java.io.IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not make the template."); }
    }

    /**
     * Reads an Excel/CSV list of parents (one row per child). commit=false only checks and shows what
     * would happen; commit=true saves. Rows sharing a phone number become ONE parent with several children.
     */
    @Transactional
    public Map<String, Object> importFile(String fileName, byte[] bytes, boolean commit) {
        List<List<String>> sheet;
        try { sheet = SheetReader.read(fileName, bytes); } catch (IllegalArgumentException e) { throw bad(e.getMessage()); }
        int headerRow = -1;
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < Math.min(5, sheet.size()) && headerRow < 0; i++) {
            List<String> r = sheet.get(i);
            Map<String, Integer> found = new HashMap<>();
            for (int c = 0; c < r.size(); c++) {
                String h = r.get(c).toLowerCase(Locale.ROOT);
                if (h.contains("admission") || h.equals("adm") || h.startsWith("adm ")) found.putIfAbsent("adm", c);
                else if (h.contains("other") && h.contains("phone") || h.contains("phone 2") || h.contains("phone2")) found.putIfAbsent("phone2", c);
                else if (h.contains("phone") || h.contains("mobile") || h.contains("tel")) found.putIfAbsent("phone", c);
                else if (h.contains("parent") || h.contains("guardian")) found.putIfAbsent("name", c);
                else if (h.contains("relation")) found.putIfAbsent("rel", c);
                else if (h.contains("mail")) found.putIfAbsent("email", c);
            }
            if (found.containsKey("adm") && found.containsKey("phone") && found.containsKey("name")) { headerRow = i; col = found; }
        }
        if (headerRow < 0) throw bad("Couldn't find the headings. The first row needs columns for the admission number, the parent's name and the phone (use the template).");

        Map<String, Student> byAdm = new HashMap<>();
        for (Student s : studentService.getAllStudents()) if (s.getAdmissionNumber() != null) byAdm.put(adm(s.getAdmissionNumber()), s);
        Set<String> existingLinks = new HashSet<>();
        linkRepository.findAll().forEach(l -> existingLinks.add(l.getGuardian().getGuardianId() + ":" + l.getStudentId()));

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Guardian> newByPhone = new LinkedHashMap<>();
        Map<String, Guardian> existingByPhone = new HashMap<>();
        List<Object[]> toLink = new ArrayList<>();             // [Guardian, studentId]
        Set<String> linkedInFile = new HashSet<>();
        int blank = 0;
        for (int i = headerRow + 1; i < sheet.size(); i++) {
            List<String> r = sheet.get(i);
            String a = cell(r, col.get("adm")), name = cell(r, col.get("name")), ph = cell(r, col.get("phone"));
            if (a.isEmpty() && name.isEmpty() && ph.isEmpty()) continue;
            if (name.isEmpty() && ph.isEmpty()) { blank++; continue; }                      // template row not filled in
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("row", i + 1);
            row.put("admissionNumber", a);
            row.put("parent", name.toUpperCase());
            Student st = a.isEmpty() ? null : byAdm.get(adm(a));
            row.put("learner", st == null ? null : (st.getFirstName() + " " + st.getLastName()).trim());
            String phone = null, problem = null;
            try { phone = phone(ph); } catch (ResponseStatusException e) { problem = "Phone \"" + ph + "\" is not valid"; }
            if (a.isEmpty()) problem = "No admission number";
            else if (st == null) problem = "No learner with admission number " + a;
            else if (name.length() < 3) problem = "No parent name";
            else if (phone == null && problem == null) problem = "No phone number";
            String phone2 = null;
            if (problem == null && !cell(r, col.get("phone2")).isEmpty()) {
                try { phone2 = phone(cell(r, col.get("phone2"))); } catch (ResponseStatusException e) { problem = "Other phone \"" + cell(r, col.get("phone2")) + "\" is not valid"; }
            }
            row.put("phone", phone);
            if (problem != null) { row.put("action", "PROBLEM"); row.put("problem", problem); rows.add(row); continue; }
            Guardian g = existingByPhone.computeIfAbsent(phone, p -> guardianRepository.findByPhone(p).orElse(null));
            String action;
            if (g != null) action = "ADD_TO_EXISTING";
            else {
                g = newByPhone.get(phone);
                action = g == null ? "NEW_PARENT" : "SAME_FAMILY";
                if (g == null) {
                    g = new Guardian();
                    g.setFullName(trim(name, 100).toUpperCase());
                    g.setPhone(phone);
                    g.setPhone2(phone2);
                    g.setRelationship(trim(cell(r, col.get("rel")), 30));
                    g.setEmail(trim(cell(r, col.get("email")), 100));
                    newByPhone.put(phone, g);
                }
            }
            String key = (g.getGuardianId() == null ? "new" + phone : String.valueOf(g.getGuardianId())) + ":" + st.getStudentId();
            if (existingLinks.contains(key) || !linkedInFile.add(key)) { row.put("action", "ALREADY"); row.put("problem", "Already linked to this parent"); rows.add(row); continue; }
            if (g.getGuardianId() != null && !g.getFullName().equalsIgnoreCase(name.trim())) row.put("note", "Phone already belongs to " + g.getFullName() + " — the child is added to them");
            row.put("action", action);
            rows.add(row);
            toLink.add(new Object[]{g, st.getStudentId()});
        }
        long problems = rows.stream().filter(x -> "PROBLEM".equals(x.get("action"))).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("newParents", newByPhone.size());
        out.put("links", toLink.size());
        out.put("problems", problems);
        out.put("leftBlank", blank);
        if (commit) {
            if (toLink.isEmpty()) throw bad("Nothing to import" + (problems > 0 ? " — fix the rows marked as problems first." : "."));
            for (Guardian g : newByPhone.values()) guardianRepository.save(g);
            for (Object[] x : toLink) { GuardianLink l = new GuardianLink(); l.setGuardian((Guardian) x[0]); l.setStudentId((Long) x[1]); linkRepository.save(l); }
            out.put("message", "Imported: " + newByPhone.size() + " new parent(s), " + toLink.size() + " child link(s)" + (problems > 0 ? "; " + problems + " row(s) with problems were skipped." : "."));
        }
        out.put("committed", commit);
        return out;
    }

    private static String cell(List<String> r, Integer c) { return c == null || c >= r.size() || r.get(c) == null ? "" : r.get(c).trim(); }
    private static String adm(String v) { return v.trim().toUpperCase().replaceAll("\\.0+$", ""); }

    /** guardianId → its children's ids (for fee reminders). */
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> childrenByGuardian() {
        Map<Long, List<Long>> out = new HashMap<>();
        linkRepository.findAll().forEach(l -> out.computeIfAbsent(l.getGuardian().getGuardianId(), k -> new ArrayList<>()).add(l.getStudentId()));
        return out;
    }

    private Map<Long, Student> students() {
        Map<Long, Student> m = new HashMap<>();
        for (Student s : studentService.getAllStudents()) m.put(s.getStudentId(), s);
        return m;
    }

    static Map<String, Object> child(Student s) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("studentId", s.getStudentId());
        c.put("name", (s.getFirstName() + " " + s.getLastName()).trim());
        c.put("admissionNumber", s.getAdmissionNumber());
        c.put("className", s.getSchoolClass() != null ? s.getSchoolClass().getClassName() : null);
        return c;
    }

    private static Map<String, Object> map(Guardian g, List<GuardianLink> links, Map<Long, Student> students) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("guardianId", g.getGuardianId());
        m.put("fullName", g.getFullName());
        m.put("phone", g.getPhone());
        m.put("phone2", g.getPhone2());
        m.put("email", g.getEmail());
        m.put("relationship", g.getRelationship());
        m.put("notes", g.getNotes());
        m.put("children", links.stream().map(l -> students.get(l.getStudentId())).filter(Objects::nonNull).map(GuardianService::child).toList());
        return m;
    }

    private static String trim(String v, int max) { if (v == null || v.isBlank()) return null; String t = v.trim(); return t.length() > max ? t.substring(0, max) : t; }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
}
