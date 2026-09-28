package jnm.engineer.demo.services;

import jnm.engineer.demo.models.*;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Subject;
import jnm.engineer.demo.models.SubjectLesson;
import jnm.engineer.demo.models.Teacher;
import jnm.engineer.demo.models.TeachingAssignment;
import jnm.engineer.demo.models.TimetableEntry;
import jnm.engineer.demo.models.TimetableSlot;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Teaching timetable.
 *   • Bell times per section (TimetableSlot) — sections can have different lesson lengths.
 *   • Lessons per week per subject per grade (SubjectLesson).
 *   • Each class's week (TimetableEntry), Monday (1) to Friday (5).
 * A teacher can never be in two places at once: clashes are checked by real clock time,
 * so a Grade 6 lesson 08:35–09:10 and a Grade 7 lesson 08:40–09:20 clash even though
 * they are different periods.
 */
@Service
@RequiredArgsConstructor
public class TimetableService {
    public static final int DAYS = 5;
    private static final String[] DAY_NAMES = {"", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
    private static final int MAX_SLOTS = 20;

    private final TimetableSlotRepository slotRepository;
    private final SubjectLessonRepository lessonRepository;
    private final TimetableEntryRepository entryRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final SectionSettingRepository sectionSettingRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final GradeLevelSettingRepository gradeRepository;

    public record SlotIn(String label, String start, String end, Boolean lesson) {}
    public record LessonIn(Long subjectId, Integer lessons, String latestEnd, Boolean doublesAllowed) {}
    public record EntryIn(Integer day, Long slotId, Long subjectId, Long teacherId) {}
    // Starting values from default-settings.json → "timetable"
    public record LessonSeed(List<String> names, Integer perWeek, String mustEndBy, Boolean doubles) {}
    public record SectionSeed(List<SlotIn> slots, List<LessonSeed> lessons) {}
    public record Defaults(Map<String, SectionSeed> timetable) {}

    // ══ Bell times ══════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Map<String, Object> slots() {
        List<Map<String, Object>> sections = new ArrayList<>();
        for (String code : settingsService.sectionCodes()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("name", settingsService.sectionName(code));
            m.put("slots", slotRepository.findBySectionOrderByPositionAsc(code).stream().map(TimetableService::slotMap).toList());
            sections.add(m);
        }
        return Map.of("sections", sections, "days", dayList());
    }

    @Transactional
    public Map<String, Object> saveSlots(String section, List<SlotIn> in) {
        if (!settingsService.sectionCodes().contains(section)) throw bad("Unknown section.");
        List<SlotIn> list = in == null ? List.of() : in;
        if (list.size() > MAX_SLOTS) throw bad("At most " + MAX_SLOTS + " periods in a day.");
        LocalTime prevEnd = null;
        for (int i = 0; i < list.size(); i++) {
            SlotIn s = list.get(i);
            String label = s.label() == null ? "" : s.label().trim();
            if (label.isEmpty() || label.length() > 40) throw bad("Period " + (i + 1) + " needs a name (40 letters at most).");
            LocalTime a = time(s.start(), label), b = time(s.end(), label);
            if (!a.isBefore(b)) throw bad(label + ": the end time must be after the start time.");
            if (prevEnd != null && a.isBefore(prevEnd)) throw bad(label + " starts before the period above it ends. Keep the periods in time order.");
            prevEnd = b;
        }
        List<TimetableSlot> existing = slotRepository.findBySectionOrderByPositionAsc(section);
        int cleared = 0;
        for (int i = 0; i < Math.max(existing.size(), list.size()); i++) {
            if (i < list.size()) {
                SlotIn s = list.get(i);
                TimetableSlot slot = i < existing.size() ? existing.get(i) : new TimetableSlot();
                boolean nowLesson = s.lesson() == null || s.lesson();
                if (slot.getSlotId() != null && slot.isLesson() && !nowLesson) cleared += clearSlot(slot);   // became a break
                slot.setSection(section);
                slot.setPosition(i + 1);
                slot.setLabel(s.label().trim());
                slot.setStartTime(time(s.start(), s.label()));
                slot.setEndTime(time(s.end(), s.label()));
                slot.setLesson(nowLesson);
                slotRepository.save(slot);
            } else {                                     // period removed
                TimetableSlot gone = existing.get(i);
                cleared += clearSlot(gone);
                slotRepository.delete(gone);
            }
        }
        return Map.of("message", settingsService.sectionName(section) + " bell times saved."
                + (cleared > 0 ? " " + cleared + " placed lesson(s) in removed or changed periods were cleared." : ""));
    }

    private int clearSlot(TimetableSlot slot) {
        List<TimetableEntry> e = entryRepository.findBySlotSlotId(slot.getSlotId());
        entryRepository.deleteAll(e);
        return e.size();
    }

    // ══ Lessons per week ════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lessons(String gradeLevel) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SubjectLesson l : lessonRepository.findByGradeLevel(grade(gradeLevel))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("subjectId", l.getSubject().getSubjectId());
            m.put("subjectName", l.getSubject().getSubjectName());
            m.put("lessons", l.getLessonsPerWeek());
            m.put("latestEnd", l.getLatestEnd() != null ? l.getLatestEnd().toString() : null);
            m.put("doublesAllowed", Boolean.TRUE.equals(l.getDoublesAllowed()));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> saveLessons(String gradeLevel, List<LessonIn> in) {
        String g = grade(gradeLevel);
        int n = 0;
        for (LessonIn l : in == null ? List.<LessonIn>of() : in) {
            if (l.subjectId() == null) continue;
            int count = l.lessons() == null ? 0 : l.lessons();
            if (count < 0 || count > 20) throw bad("Lessons a week must be between 0 and 20.");
            LocalTime latest = l.latestEnd() == null || l.latestEnd().isBlank() ? null : time(l.latestEnd(), "Must end by");
            boolean doubles = Boolean.TRUE.equals(l.doublesAllowed());
            Optional<SubjectLesson> cur = lessonRepository.findByGradeLevelAndSubjectSubjectId(g, l.subjectId());
            if (count == 0 && latest == null && !doubles) { cur.ifPresent(lessonRepository::delete); n++; continue; }
            Subject subject = subjectRepository.findById(l.subjectId()).orElseThrow(() -> bad("A subject no longer exists — refresh the page."));
            SubjectLesson row = cur.orElseGet(SubjectLesson::new);
            row.setGradeLevel(g);
            row.setSubject(subject);
            row.setLessonsPerWeek(count);
            row.setLatestEnd(latest);
            row.setDoublesAllowed(doubles);
            lessonRepository.save(row);
            n++;
        }
        return Map.of("message", "Lessons per week saved for " + g + " (" + n + " subject(s)).");
    }

    // ══ A class's week ══════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Map<String, Object> classView(Long classId) {
        SchoolClass cls = schoolClassRepository.findById(classId).orElseThrow(() -> notFound("Class not found."));
        String section = cls.getSection();
        boolean subjectTeaching = section != null && sectionSettingRepository.findById(section)
                .map(s -> Boolean.TRUE.equals(s.getSubjectTeaching())).orElse(false);
        Teacher classTeacher = cls.getClassTeacher();

        Map<Long, Teacher> assigned = new HashMap<>();
        for (TeachingAssignment a : teachingAssignmentRepository.findBySchoolClassClassId(classId)) assigned.put(a.getSubject().getSubjectId(), a.getTeacher());
        Map<Long, SubjectLesson> rules = rulesFor(cls);
        List<Map<String, Object>> subjects = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        classSubjectRepository.findBySchoolClassClassId(classId).forEach(cs -> {
            Subject s = cs.getSubject();
            if (s == null || !seen.add(s.getSubjectId())) return;
            Teacher t = assigned.getOrDefault(s.getSubjectId(), subjectTeaching ? null : classTeacher);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("subjectId", s.getSubjectId());
            m.put("subjectName", s.getSubjectName());
            m.put("subjectCode", s.getSubjectCode());
            SubjectLesson rule = rules.get(s.getSubjectId());
            m.put("lessons", rule != null ? rule.getLessonsPerWeek() : 0);
            m.put("latestEnd", rule != null && rule.getLatestEnd() != null ? rule.getLatestEnd().toString() : null);
            m.put("doublesAllowed", rule != null && Boolean.TRUE.equals(rule.getDoublesAllowed()));
            m.put("teacherId", t != null ? t.getTeacherId() : null);
            m.put("teacherName", teacherName(t));
            subjects.add(m);
        });
        subjects.sort(Comparator.comparing(m -> String.valueOf(m.get("subjectName"))));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("classId", cls.getClassId());
        out.put("className", cls.getClassName());
        out.put("stream", cls.getStream());
        out.put("gradeLevel", cls.getGradeLevel());
        out.put("section", section);
        out.put("sectionName", settingsService.sectionName(section));
        out.put("subjectTeaching", subjectTeaching);
        out.put("classTeacherId", classTeacher != null ? classTeacher.getTeacherId() : null);
        out.put("classTeacherName", teacherName(classTeacher));
        out.put("days", dayList());
        out.put("slots", section == null ? List.of() : slotRepository.findBySectionOrderByPositionAsc(section).stream().map(TimetableService::slotMap).toList());
        out.put("subjects", subjects);
        out.put("entries", entryRepository.findBySchoolClassClassId(classId).stream().map(TimetableService::entryMap).toList());
        return out;
    }

    /**
     * Replaces a class's week. Refused (nothing saved) if any teacher would be teaching
     * somewhere else at an overlapping time.
     */
    @Transactional
    public Map<String, Object> saveClass(Long classId, List<EntryIn> in) {
        SchoolClass cls = schoolClassRepository.findById(classId).orElseThrow(() -> notFound("Class not found."));
        Map<Long, TimetableSlot> slots = new HashMap<>();
        if (cls.getSection() != null) for (TimetableSlot s : slotRepository.findBySectionOrderByPositionAsc(cls.getSection())) slots.put(s.getSlotId(), s);
        Map<Long, Subject> classSubjects = new HashMap<>();
        classSubjectRepository.findBySchoolClassClassId(classId).forEach(cs -> { if (cs.getSubject() != null) classSubjects.put(cs.getSubject().getSubjectId(), cs.getSubject()); });

        List<EntryIn> list = in == null ? List.of() : in;
        Set<String> used = new HashSet<>();
        Map<Long, Teacher> teachers = new HashMap<>();
        List<TimetableEntry> fresh = new ArrayList<>();
        for (EntryIn e : list) {
            if (e.day() == null || e.day() < 1 || e.day() > DAYS) throw bad("A lesson has a day outside Monday–Friday.");
            TimetableSlot slot = slots.get(e.slotId());
            if (slot == null) throw bad("A lesson is in a period that no longer exists — refresh the page.");
            if (!slot.isLesson()) throw bad(slot.getLabel() + " is a break, not a lesson period.");
            if (!used.add(e.day() + ":" + e.slotId())) throw bad("Two lessons are in the same period on " + DAY_NAMES[e.day()] + ".");
            Subject subject = classSubjects.get(e.subjectId());
            if (subject == null) throw bad("A subject is not on " + cls.getClassName() + "'s subject list — refresh the page.");
            Teacher t = null;
            if (e.teacherId() != null) {
                t = teachers.computeIfAbsent(e.teacherId(), id -> teacherRepository.findById(id).orElse(null));
                if (t == null) throw bad("A teacher no longer exists — refresh the page.");
            }
            TimetableEntry te = new TimetableEntry();
            te.setSchoolClass(cls);
            te.setDayOfWeek(e.day());
            te.setSlot(slot);
            te.setSubject(subject);
            te.setTeacher(t);
            fresh.add(te);
        }

        // The school's subject rules: "must end by" and no back-to-back (double) lessons unless allowed
        Map<Long, SubjectLesson> rules = rulesFor(cls);
        List<TimetableSlot> lessonOrder = slots.values().stream().filter(TimetableSlot::isLesson)
                .sorted(Comparator.comparing(TimetableSlot::getPosition)).toList();
        List<String> broken = new ArrayList<>();
        for (TimetableEntry te : fresh) {
            SubjectLesson rule = rules.get(te.getSubject().getSubjectId());
            if (rule != null && rule.getLatestEnd() != null && te.getSlot().getEndTime().isAfter(rule.getLatestEnd())) {
                broken.add(te.getSubject().getSubjectName() + " must end by " + rule.getLatestEnd() + " — not " + DAY_NAMES[te.getDayOfWeek()]
                        + " " + te.getSlot().getStartTime() + "–" + te.getSlot().getEndTime());
            }
            boolean doublesOk = rule != null && Boolean.TRUE.equals(rule.getDoublesAllowed());
            int i = lessonOrder.indexOf(te.getSlot());
            if (!doublesOk && i > 0) {
                TimetableSlot before = lessonOrder.get(i - 1);
                boolean backToBack = !before.getEndTime().isBefore(te.getSlot().getStartTime());   // no break between them
                boolean sameBefore = fresh.stream().anyMatch(o -> o.getDayOfWeek().equals(te.getDayOfWeek()) && o.getSlot() == before
                        && o.getSubject().getSubjectId().equals(te.getSubject().getSubjectId()));
                if (backToBack && sameBefore) broken.add(te.getSubject().getSubjectName() + " has a double lesson on " + DAY_NAMES[te.getDayOfWeek()]
                        + " (" + before.getStartTime() + " and " + te.getSlot().getStartTime() + "). Double lessons are off for this subject.");
            }
        }
        if (!broken.isEmpty()) {
            List<String> show = broken.size() > 8 ? broken.subList(0, 8) : broken;
            throw bad("Not saved — the timetable breaks the subject rules:\n• " + String.join("\n• ", show)
                    + (broken.size() > 8 ? "\n…and " + (broken.size() - 8) + " more." : ""));
        }

        // Clashes with other classes, by clock time
        List<String> clashes = new ArrayList<>();
        List<TimetableEntry> others = entryRepository.findByTeacherIsNotNull().stream()
                .filter(o -> !o.getSchoolClass().getClassId().equals(classId)).toList();
        for (TimetableEntry mine : fresh) {
            if (mine.getTeacher() == null) continue;
            for (TimetableEntry o : others) {
                if (!o.getTeacher().getTeacherId().equals(mine.getTeacher().getTeacherId()) || !o.getDayOfWeek().equals(mine.getDayOfWeek())) continue;
                if (overlaps(mine.getSlot().getStartTime(), mine.getSlot().getEndTime(), o.getSlot().getStartTime(), o.getSlot().getEndTime())) {
                    clashes.add(teacherName(mine.getTeacher()) + " is already teaching " + o.getSchoolClass().getClassName() + " "
                            + o.getSubject().getSubjectName() + " on " + DAY_NAMES[mine.getDayOfWeek()] + " "
                            + o.getSlot().getStartTime() + "–" + o.getSlot().getEndTime()
                            + " (clashes with " + mine.getSubject().getSubjectName() + " " + mine.getSlot().getStartTime() + "–" + mine.getSlot().getEndTime() + ")");
                }
            }
        }
        if (!clashes.isEmpty()) {
            List<String> show = clashes.size() > 8 ? clashes.subList(0, 8) : clashes;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not saved — a teacher would be in two places at once:\n• "
                    + String.join("\n• ", show) + (clashes.size() > 8 ? "\n…and " + (clashes.size() - 8) + " more." : ""));
        }

        entryRepository.deleteAll(entryRepository.findBySchoolClassClassId(classId));
        entryRepository.flush();
        entryRepository.saveAll(fresh);
        return Map.of("message", cls.getClassName() + " timetable saved (" + fresh.size() + " lessons).");
    }

    /** Other classes' lessons, by teacher and time — used by the screen to warn early and to auto-fill. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> busy(Long excludeClassId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TimetableEntry e : entryRepository.findByTeacherIsNotNull()) {
            if (excludeClassId != null && e.getSchoolClass().getClassId().equals(excludeClassId)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("teacherId", e.getTeacher().getTeacherId());
            m.put("day", e.getDayOfWeek());
            m.put("start", e.getSlot().getStartTime().toString());
            m.put("end", e.getSlot().getEndTime().toString());
            m.put("className", e.getSchoolClass().getClassName());
            m.put("subjectName", e.getSubject().getSubjectName());
            out.add(m);
        }
        return out;
    }

    // ══ A teacher's week ════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Map<String, Object> teacherView(Long teacherId) {
        Teacher t = teacherRepository.findById(teacherId).orElseThrow(() -> notFound("Teacher not found."));
        List<Map<String, Object>> lessons = new ArrayList<>();
        for (TimetableEntry e : entryRepository.findByTeacherTeacherId(teacherId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", e.getDayOfWeek());
            m.put("start", e.getSlot().getStartTime().toString());
            m.put("end", e.getSlot().getEndTime().toString());
            m.put("period", e.getSlot().getLabel());
            m.put("classId", e.getSchoolClass().getClassId());
            m.put("className", e.getSchoolClass().getClassName());
            m.put("stream", e.getSchoolClass().getStream());
            m.put("section", e.getSchoolClass().getSection());
            m.put("subjectName", e.getSubject().getSubjectName());
            m.put("subjectCode", e.getSubject().getSubjectCode());
            lessons.add(m);
        }
        lessons.sort(Comparator.comparing((Map<String, Object> m) -> (Integer) m.get("day")).thenComparing(m -> String.valueOf(m.get("start"))));
        return Map.of("teacherId", t.getTeacherId(), "teacherName", teacherName(t), "days", dayList(), "lessons", lessons);
    }

    /** The logged-in teacher's own week. */
    @Transactional(readOnly = true)
    public Map<String, Object> mine(String username) {
        User u = userRepository.findByUsername(username).orElseThrow(() -> notFound("Account not found."));
        Long teacherId = u.getTeacher() != null ? u.getTeacher().getTeacherId() : (u.getRole() == User.Role.TEACHER ? u.getLinkedId() : null);
        if (teacherId == null) throw notFound("Your account isn't linked to a teacher, so there is no personal timetable.");
        return teacherView(teacherId);
    }

    // ══ Starting values (only where nothing is set yet) ══════════════════════
    /**
     * Loads bell times into a section that has none, and lessons per week (with rules) into a
     * grade that has none. Never changes anything already set. Returns what it did, for the log.
     */
    @Transactional
    public List<String> seedDefaults(Defaults d) {
        List<String> log = new ArrayList<>();
        if (d == null || d.timetable() == null) return log;
        List<String> sections = settingsService.sectionCodes();
        for (Map.Entry<String, SectionSeed> e : d.timetable().entrySet()) {
            String section = e.getKey();
            SectionSeed seed = e.getValue();
            if (!sections.contains(section) || seed == null) continue;

            // Bell times
            if (seed.slots() != null && !seed.slots().isEmpty() && slotRepository.findBySectionOrderByPositionAsc(section).isEmpty()) {
                int pos = 1;
                for (SlotIn s : seed.slots()) {
                    TimetableSlot slot = new TimetableSlot();
                    slot.setSection(section);
                    slot.setPosition(pos++);
                    slot.setLabel(s.label());
                    slot.setStartTime(time(s.start(), s.label()));
                    slot.setEndTime(time(s.end(), s.label()));
                    slot.setLesson(s.lesson() == null || s.lesson());
                    slotRepository.save(slot);
                }
                log.add(settingsService.sectionName(section) + ": bell times set (" + seed.slots().size() + " periods)");
            }

            // Lessons per week, per grade of the section
            if (seed.lessons() == null || seed.lessons().isEmpty()) continue;
            List<Subject> subjects = subjectRepository.findAll().stream()
                    .filter(s -> section.equals(settingsService.sectionOfGrade(s.getGradeLevel() == null ? "" : s.getGradeLevel()))).toList();
            List<String> unmatched = new ArrayList<>();
            Map<LessonSeed, Subject> match = new LinkedHashMap<>();
            for (LessonSeed l : seed.lessons()) {
                Subject found = null;
                for (String n : l.names() == null ? List.<String>of() : l.names()) {
                    found = subjects.stream().filter(s -> simple(s.getSubjectName()).equals(simple(n))).findFirst().orElse(null);
                    if (found != null) break;
                }
                if (found != null) match.put(l, found);
                else if (l.names() != null && !l.names().isEmpty()) unmatched.add(l.names().get(0));
            }
            for (GradeLevelSetting g : gradeRepository.findAll()) {
                if (!section.equals(g.getSectionCode()) || !lessonRepository.findByGradeLevel(g.getCode()).isEmpty()) continue;
                for (Map.Entry<LessonSeed, Subject> m : match.entrySet()) {
                    SubjectLesson row = new SubjectLesson();
                    row.setGradeLevel(g.getCode());
                    row.setSubject(m.getValue());
                    row.setLessonsPerWeek(m.getKey().perWeek() == null ? 0 : m.getKey().perWeek());
                    row.setLatestEnd(m.getKey().mustEndBy() == null || m.getKey().mustEndBy().isBlank() ? null : time(m.getKey().mustEndBy(), "mustEndBy"));
                    row.setDoublesAllowed(Boolean.TRUE.equals(m.getKey().doubles()));
                    lessonRepository.save(row);
                }
                if (!match.isEmpty()) log.add(g.getCode() + ": lessons per week set for " + match.size() + " subject(s)");
            }
            if (!unmatched.isEmpty()) log.add(settingsService.sectionName(section) + ": no subject found for " + String.join(", ", unmatched)
                    + " — set these on Timetable → Setup");
        }
        return log;
    }

    /** "Science & Tech." → "sciencetech": letters and digits only, lower case, "and" and "&" ignored. */
    private static String simple(String v) {
        return String.valueOf(v == null ? "" : v).toLowerCase().replace("&", " ").replaceAll("\\band\\b", " ").replaceAll("[^a-z0-9]", "");
    }

    // ══ helpers ═════════════════════════════════════════════════════════════
    private Map<Long, SubjectLesson> rulesFor(SchoolClass cls) {
        Map<Long, SubjectLesson> m = new HashMap<>();
        if (cls.getGradeLevel() != null) {
            for (SubjectLesson l : lessonRepository.findByGradeLevel(cls.getGradeLevel().trim().toUpperCase())) m.put(l.getSubject().getSubjectId(), l);
        }
        return m;
    }

    /** Two lessons overlap if each starts before the other ends (touching end-to-start is fine). */
    public static boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    private static List<Map<String, Object>> dayList() {
        List<Map<String, Object>> d = new ArrayList<>();
        for (int i = 1; i <= DAYS; i++) d.add(Map.of("day", i, "name", DAY_NAMES[i]));
        return d;
    }

    private static Map<String, Object> slotMap(TimetableSlot s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slotId", s.getSlotId());
        m.put("position", s.getPosition());
        m.put("label", s.getLabel());
        m.put("start", s.getStartTime().toString());
        m.put("end", s.getEndTime().toString());
        m.put("lesson", s.isLesson());
        return m;
    }

    private static Map<String, Object> entryMap(TimetableEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("day", e.getDayOfWeek());
        m.put("slotId", e.getSlot().getSlotId());
        m.put("subjectId", e.getSubject().getSubjectId());
        m.put("subjectName", e.getSubject().getSubjectName());
        m.put("subjectCode", e.getSubject().getSubjectCode());
        m.put("teacherId", e.getTeacher() != null ? e.getTeacher().getTeacherId() : null);
        m.put("teacherName", teacherName(e.getTeacher()));
        return m;
    }

    private static String teacherName(Teacher t) {
        if (t == null) return null;
        return ((t.getFirstName() == null ? "" : t.getFirstName()) + " " + (t.getLastName() == null ? "" : t.getLastName())).trim();
    }

    private String grade(String g) {
        String v = g == null ? "" : g.trim().toUpperCase();
        if (settingsService.sectionOfGrade(v) == null) throw bad("Unknown grade.");
        return v;
    }

    private static LocalTime time(String v, String label) {
        try { return LocalTime.parse(String.valueOf(v).trim()); }
        catch (DateTimeParseException e) { throw bad(label + ": enter times like 08:00."); }
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
}
