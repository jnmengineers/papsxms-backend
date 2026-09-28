package jnm.engineer.demo.services;

import jnm.engineer.demo.models.*;
import jnm.engineer.demo.models.ExamTypeSetting;
import jnm.engineer.demo.models.GradeLevelSetting;
import jnm.engineer.demo.models.SchoolProfile;
import jnm.engineer.demo.models.SectionSetting;
import jnm.engineer.demo.models.StreamSetting;
import jnm.engineer.demo.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * School-wide settings, all stored in the database: the school profile, sections, grades
 * (with promotion order), streams and exam types. Pages load them once after login.
 *
 * CODES (PRE_SCHOOL, G4, YELLOW, END_TERM…) are stored on classes, exams, fees etc., so they
 * never change here; names, colours, targets, order and promotion can.
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern IMAGE = Pattern.compile("^data:image/(png|jpeg|jpg|webp|gif|svg\\+xml);base64,[A-Za-z0-9+/=]+$");
    private static final int MAX_LOGO_CHARS = 700_000;       // ≈ 500 KB image

    private final SchoolProfileRepository profileRepository;
    private final SectionSettingRepository sectionRepository;
    private final GradeLevelSettingRepository gradeRepository;
    private final StreamSettingRepository streamRepository;
    private final ExamTypeSettingRepository examTypeRepository;

    public record ProfileIn(String name, String shortName, String motto, String postalAddress, String phones,
                            String email, String website, String bankName, String bankBranch, String bankAccountName,
                            String bankAccountNumber, String paybillNumber, String paybillAccountHint, String paymentNote,
                            String logoLeft, String logoRight) {}
    public record SectionIn(String code, String name, String color, Double meanTarget, Integer sortOrder, Boolean subjectTeaching) {}
    public record GradeIn(String code, String name, String sectionCode, Integer sortOrder, String nextGradeCode) {}
    public record StreamIn(String code, String name, String color, Integer sortOrder, Boolean active) {}
    public record ExamTypeIn(String code, String name, String color, Integer sortOrder, Boolean core, Boolean active) {}

    /** Section codes in their set order — used by fees, extra charges, etc. */
    @Transactional(readOnly = true)
    public List<String> sectionCodes() {
        List<String> out = new ArrayList<>();
        sorted(sectionRepository.findAll(), SectionSetting::getSortOrder).forEach(x -> out.add(x.getCode()));
        return out;
    }

    /** The grade a class name starts with ("G4Y" → "G4"), using the grades in Settings. Longest code wins. */
    @Transactional(readOnly = true)
    public String gradeFromClassName(String className) {
        if (className == null) return null;
        String name = className.trim().toUpperCase();
        return gradeRepository.findAll().stream().map(GradeLevelSetting::getCode)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .filter(c -> name.startsWith(c.toUpperCase())
                        && (name.length() == c.length() || !Character.isDigit(name.charAt(c.length()))))
                .findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public String sectionOfGrade(String grade) {
        return grade == null ? null : gradeRepository.findById(grade.trim().toUpperCase()).map(GradeLevelSetting::getSectionCode).orElse(null);
    }

    /** The section's name from Settings ("Upper Primary"), or the code if unknown. */
    @Transactional(readOnly = true)
    public String sectionName(String section) {
        return section == null ? "" : sectionRepository.findById(section).map(SectionSetting::getName).orElse(section);
    }

    @Transactional(readOnly = true)
    public Double targetOfSection(String section) {
        return section == null ? null : sectionRepository.findById(section).map(SectionSetting::getMeanTarget).orElse(null);
    }

    // ══ Read ════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Map<String, Object> all() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profile", profileRepository.findById(1L).orElse(null));
        out.put("sections", sorted(sectionRepository.findAll(), SectionSetting::getSortOrder));
        out.put("grades", sorted(gradeRepository.findAll(), GradeLevelSetting::getSortOrder));
        out.put("streams", sorted(streamRepository.findAll(), StreamSetting::getSortOrder));
        out.put("examTypes", sorted(examTypeRepository.findAll(), ExamTypeSetting::getSortOrder));
        return out;
    }

    /** What the login page may show before anyone logs in: name, motto and logos only. */
    @Transactional(readOnly = true)
    public Map<String, Object> publicInfo() {
        SchoolProfile p = profileRepository.findById(1L).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", p != null ? p.getName() : null);
        out.put("shortName", p != null ? p.getShortName() : null);
        out.put("motto", p != null ? p.getMotto() : null);
        out.put("logoLeft", p != null ? p.getLogoLeft() : null);
        out.put("logoRight", p != null ? p.getLogoRight() : null);
        return out;
    }

    // ══ Update ══════════════════════════════════════════════════════════════
    @Transactional
    public SchoolProfile saveProfile(ProfileIn in) {
        String name = trim(in.name(), 120);
        if (name == null) throw bad("The school name is required.");
        SchoolProfile p = profileRepository.findById(1L).orElseGet(SchoolProfile::new);
        p.setId(1L);
        p.setName(name);
        p.setShortName(trim(in.shortName(), 60));
        p.setMotto(trim(in.motto(), 160));
        p.setPostalAddress(trim(in.postalAddress(), 160));
        p.setPhones(trim(in.phones(), 120));
        String email = trim(in.email(), 100);
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw bad("The email address doesn't look right.");
        p.setEmail(email);
        p.setWebsite(trim(in.website(), 100));
        p.setBankName(trim(in.bankName(), 60));
        p.setBankBranch(trim(in.bankBranch(), 60));
        p.setBankAccountName(trim(in.bankAccountName(), 120));
        p.setBankAccountNumber(trim(in.bankAccountNumber(), 40));
        p.setPaybillNumber(trim(in.paybillNumber(), 20));
        p.setPaybillAccountHint(trim(in.paybillAccountHint(), 80));
        p.setPaymentNote(trim(in.paymentNote(), 200));
        p.setLogoLeft(logo(in.logoLeft(), "Left logo"));
        p.setLogoRight(logo(in.logoRight(), "Right logo"));
        return profileRepository.save(p);
    }

    @Transactional
    public List<SectionSetting> saveSections(List<SectionIn> list) {
        for (SectionIn in : list) {
            SectionSetting s = sectionRepository.findById(code(in.code()))
                    .orElseThrow(() -> bad("Unknown section " + in.code() + ". Sections can be renamed, not added."));
            s.setName(required(in.name(), 60, "Section name"));
            s.setColor(color(in.color()));
            if (in.meanTarget() == null || in.meanTarget() < 1 || in.meanTarget() > 100) throw bad(s.getName() + ": mean target must be between 1 and 100.");
            s.setMeanTarget(in.meanTarget());
            if (in.sortOrder() != null) s.setSortOrder(in.sortOrder());
            if (in.subjectTeaching() != null) s.setSubjectTeaching(in.subjectTeaching());
            sectionRepository.save(s);
        }
        return sorted(sectionRepository.findAll(), SectionSetting::getSortOrder);
    }

    @Transactional
    public List<GradeLevelSetting> saveGrades(List<GradeIn> list) {
        Set<String> sections = new HashSet<>();
        sectionRepository.findAll().forEach(s -> sections.add(s.getCode()));
        Map<String, GradeLevelSetting> grades = new HashMap<>();
        gradeRepository.findAll().forEach(g -> grades.put(g.getCode(), g));
        for (GradeIn in : list) {
            GradeLevelSetting g = grades.get(code(in.code()));
            if (g == null) throw bad("Unknown grade " + in.code() + ". Grades can be edited, not added.");
            g.setName(required(in.name(), 40, "Grade name"));
            String sec = code(in.sectionCode());
            if (!sections.contains(sec)) throw bad(g.getCode() + ": choose a valid section.");
            g.setSectionCode(sec);
            if (in.sortOrder() != null) g.setSortOrder(in.sortOrder());
            String next = in.nextGradeCode() == null || in.nextGradeCode().isBlank() ? null : code(in.nextGradeCode());
            if (next != null && !grades.containsKey(next)) throw bad(g.getCode() + ": promotes to an unknown grade.");
            if (g.getCode().equals(next)) throw bad(g.getCode() + " can't promote to itself.");
            g.setNextGradeCode(next);
        }
        // Promotion must never loop (e.g. G8 → G9 → G8)
        for (GradeLevelSetting g : grades.values()) {
            Set<String> seen = new HashSet<>();
            String cur = g.getCode();
            while (cur != null) {
                if (!seen.add(cur)) throw bad("Promotion order goes round in a circle at " + cur + ".");
                GradeLevelSetting x = grades.get(cur);
                cur = x == null ? null : x.getNextGradeCode();
            }
        }
        gradeRepository.saveAll(grades.values());
        return sorted(gradeRepository.findAll(), GradeLevelSetting::getSortOrder);
    }

    @Transactional
    public List<StreamSetting> saveStreams(List<StreamIn> list) {
        for (StreamIn in : list) {
            String name = required(in.name(), 40, "Stream name");
            String c = in.code() == null || in.code().isBlank()
                    ? name.toUpperCase().replaceAll("[^A-Z0-9]", "")      // new stream: code from the name
                    : code(in.code());
            if (c.isEmpty() || c.length() > 20) throw bad("\"" + name + "\" can't be used as a stream name.");
            StreamSetting s = streamRepository.findById(c).orElseGet(() -> { StreamSetting x = new StreamSetting(); x.setCode(c); return x; });
            s.setName(name);
            s.setColor(color(in.color()));
            s.setSortOrder(in.sortOrder() != null ? in.sortOrder() : 99);
            s.setActive(in.active() == null || in.active());
            streamRepository.save(s);
        }
        return sorted(streamRepository.findAll(), StreamSetting::getSortOrder);
    }

    @Transactional
    public List<ExamTypeSetting> saveExamTypes(List<ExamTypeIn> list) {
        for (ExamTypeIn in : list) {
            String name = required(in.name(), 40, "Exam type name");
            String c = in.code() == null || in.code().isBlank()
                    ? name.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "")
                    : code(in.code());
            if (c.isEmpty() || c.length() > 20) throw bad("\"" + name + "\" can't be used as an exam type name.");
            ExamTypeSetting t = examTypeRepository.findById(c).orElseGet(() -> { ExamTypeSetting x = new ExamTypeSetting(); x.setCode(c); return x; });
            t.setName(name);
            t.setColor(color(in.color()));
            t.setSortOrder(in.sortOrder() != null ? in.sortOrder() : 99);
            t.setCore(in.core() != null && in.core());
            t.setActive(in.active() == null || in.active());
            examTypeRepository.save(t);
        }
        return sorted(examTypeRepository.findAll(), ExamTypeSetting::getSortOrder);
    }

    // ══ First start: fill EMPTY tables from resources/default-settings.json ═══
    /** The starting values, read from default-settings.json (no school values live in this code). */
    public record Defaults(ProfileIn profile, List<SectionIn> sections, List<GradeIn> grades,
                           List<StreamIn> streams, List<ExamTypeIn> examTypes) {}

    @Transactional
    public void seedDefaults(Defaults d) {
        if (d == null) return;
        if (profileRepository.count() == 0 && d.profile() != null) {
            saveProfile(d.profile());
        }
        if (sectionRepository.count() == 0 && d.sections() != null) {
            for (SectionIn in : d.sections()) {
                SectionSetting s = new SectionSetting();
                s.setCode(code(in.code()));
                s.setName(required(in.name(), 60, "Section name"));
                s.setColor(color(in.color()));
                s.setMeanTarget(in.meanTarget());
                s.setSortOrder(in.sortOrder());
                s.setSubjectTeaching(Boolean.TRUE.equals(in.subjectTeaching()));
                sectionRepository.save(s);
            }
        }
        if (gradeRepository.count() == 0 && d.grades() != null) {
            for (GradeIn in : d.grades()) {
                GradeLevelSetting g = new GradeLevelSetting();
                g.setCode(code(in.code()));
                g.setName(required(in.name(), 40, "Grade name"));
                g.setSectionCode(code(in.sectionCode()));
                g.setSortOrder(in.sortOrder());
                g.setNextGradeCode(in.nextGradeCode() == null || in.nextGradeCode().isBlank() ? null : code(in.nextGradeCode()));
                gradeRepository.save(g);
            }
        }
        // Sections created before "subject teaching" existed: take it from the starting values
        if (d.sections() != null) {
            for (SectionIn in : d.sections()) {
                sectionRepository.findById(code(in.code())).ifPresent(s -> {
                    if (s.getSubjectTeaching() == null) { s.setSubjectTeaching(Boolean.TRUE.equals(in.subjectTeaching())); sectionRepository.save(s); }
                });
            }
        }
        if (streamRepository.count() == 0 && d.streams() != null) saveStreams(d.streams());
        if (examTypeRepository.count() == 0 && d.examTypes() != null) saveExamTypes(d.examTypes());
    }

    // ── helpers ──
    private static <T> List<T> sorted(List<T> list, java.util.function.Function<T, Integer> key) {
        List<T> out = new ArrayList<>(list);
        out.sort(Comparator.comparing(key, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }
    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
    private static String required(String s, int max, String label) {
        String t = trim(s, max);
        if (t == null) throw bad(label + " is required.");
        return t;
    }
    private static String code(String s) { return s == null ? "" : s.trim().toUpperCase(); }
    private static String color(String c) {
        if (c == null || !COLOR.matcher(c.trim()).matches()) throw bad("Colours must look like #1F3864.");
        return c.trim();
    }
    private static String logo(String data, String label) {
        if (data == null || data.isBlank()) return null;
        if (data.length() > MAX_LOGO_CHARS) throw bad(label + " is too large — use an image under about 500 KB.");
        if (!IMAGE.matcher(data).matches()) throw bad(label + " must be a PNG, JPG, WEBP, GIF or SVG image.");
        return data;
    }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
