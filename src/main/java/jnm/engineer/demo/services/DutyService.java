package jnm.engineer.demo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jnm.engineer.demo.models.*;
import jnm.engineer.demo.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Weekly duty roster (teacher on duty and other duties). A fair automatic rota continues from where
 * the last one stopped; someone away for most of a week is skipped but keeps their place for next week.
 */
@Service
@RequiredArgsConstructor
public class DutyService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DutyKindRepository kindRepository;
    private final DutyAssignmentRepository assignmentRepository;
    private final StaffMemberRepository staffRepository;
    private final LeaveRequestRepository leaveRepository;
    private final UserRepository userRepository;

    public record KindIn(String code, String name, Integer peoplePerWeek, Boolean teachersOnly, Boolean active, Integer sortOrder, String notes) {}
    public record GenerateIn(String dutyCode, LocalDate from, Integer weeks, List<Long> staffIds, Boolean save) {}
    public record AssignIn(LocalDate weekStart, String dutyCode, Long staffId, String note) {}

    public static LocalDate monday(LocalDate d) { return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

    /** The current school week's Monday — on Saturday or Sunday, the coming week. */
    static LocalDate schoolWeek(LocalDate d) { return d.getDayOfWeek().getValue() >= 6 ? d.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) : monday(d); }

    @Transactional
    public List<Map<String, Object>> kinds() {
        seed();
        return kindRepository.findAllByOrderBySortOrderAscNameAsc().stream().map(DutyService::kindMap).toList();
    }

    @Transactional
    public Map<String, Object> saveKind(KindIn in) {
        String code = in.code() == null ? "" : in.code().trim().toUpperCase().replaceAll("[^A-Z0-9_]", "");
        if (code.length() < 2) throw bad("Enter a short code (e.g. GAMES).");
        String name = in.name() == null ? null : in.name().trim();
        if (name == null || name.isEmpty()) throw bad("Enter the name of the duty.");
        if (in.peoplePerWeek() != null && (in.peoplePerWeek() < 1 || in.peoplePerWeek() > 10)) throw bad("People per week must be 1 to 10.");
        DutyKind k = kindRepository.findById(code).orElseGet(DutyKind::new);
        k.setCode(code);
        k.setName(name.length() > 60 ? name.substring(0, 60) : name);
        k.setPeoplePerWeek(in.peoplePerWeek() == null ? 1 : in.peoplePerWeek());
        k.setTeachersOnly(in.teachersOnly() == null || in.teachersOnly());
        k.setActive(in.active() == null || in.active());
        k.setSortOrder(in.sortOrder() == null ? 50 : in.sortOrder());
        k.setNotes(in.notes() == null || in.notes().isBlank() ? null : in.notes().trim());
        kindRepository.save(k);
        return Map.of("message", k.getName() + " saved.");
    }

    /** The rota for weeks from..to: each week's Monday with the people on each duty. */
    @Transactional
    public Map<String, Object> rota(LocalDate from, LocalDate to) {
        seed();
        LocalDate a = monday(from), b = monday(to);
        if (b.isBefore(a)) throw bad("The end can't be before the start.");
        if (a.plusWeeks(60).isBefore(b)) throw bad("Show at most about a year at a time.");
        Map<LocalDate, Map<String, List<Map<String, Object>>>> weeks = new TreeMap<>();
        for (LocalDate w = a; !w.isAfter(b); w = w.plusWeeks(1)) weeks.put(w, new LinkedHashMap<>());
        for (DutyAssignment x : assignmentRepository.findByWeekStartBetweenOrderByWeekStartAsc(a, b)) {
            weeks.computeIfAbsent(x.getWeekStart(), k -> new LinkedHashMap<>()).computeIfAbsent(x.getDutyCode(), k -> new ArrayList<>()).add(assignmentMap(x));
        }
        List<Map<String, Object>> list = new ArrayList<>();
        weeks.forEach((w, duties) -> list.add(Map.of("weekStart", w.toString(), "weekEnd", w.plusDays(4).toString(), "duties", duties)));
        return Map.of("kinds", kinds(), "weeks", list);
    }

    /** A fair rota for one duty over several weeks. save=false shows it first; save=true replaces those weeks for that duty. */
    @Transactional
    public Map<String, Object> generate(GenerateIn in) {
        DutyKind k = kind(in.dutyCode());
        int weeks = in.weeks() == null ? 0 : in.weeks();
        if (weeks < 1 || weeks > 53) throw bad("Choose how many weeks (1 to 53).");
        if (in.from() == null) throw bad("Choose the first week.");
        LocalDate first = monday(in.from());
        List<StaffMember> pool = new ArrayList<>();
        for (StaffMember s : staffRepository.findByActiveTrueOrderByFullNameAsc()) {
            if (in.staffIds() != null && !in.staffIds().isEmpty() && !in.staffIds().contains(s.getStaffId())) continue;
            if (k.onlyTeachers() && !"TEACHING".equals(s.getCategory())) continue;
            pool.add(s);
        }
        if (pool.size() < k.people()) throw bad("Not enough staff for " + k.getName().toLowerCase() + " (" + k.people() + " a week, " + pool.size() + " available).");
        // Continue the rotation: whoever was on this duty most recently goes to the back of the queue
        Deque<StaffMember> queue = new ArrayDeque<>(pool);
        List<DutyAssignment> before = assignmentRepository.findByDutyCodeAndWeekStartBeforeOrderByWeekStartDesc(k.getCode(), first);
        List<Long> recent = new ArrayList<>();
        for (DutyAssignment x : before) if (!recent.contains(x.getStaff().getStaffId())) recent.add(x.getStaff().getStaffId());
        for (int i = recent.size() - 1; i >= 0; i--) {                     // least recent first … most recent last
            Long id = recent.get(i);
            queue.stream().filter(s -> s.getStaffId().equals(id)).findFirst().ifPresent(s -> { queue.remove(s); queue.addLast(s); });
        }
        List<Map<String, Object>> plan = new ArrayList<>();
        List<DutyAssignment> toSave = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            LocalDate mon = first.plusWeeks(w);
            Set<Long> away = awayMostOfWeek(mon);
            List<StaffMember> chosen = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            for (StaffMember s : new ArrayList<>(queue)) {
                if (chosen.size() == k.people()) break;
                if (away.contains(s.getStaffId())) { skipped.add(s.getFullName()); continue; }   // keeps their place at the front
                chosen.add(s);
            }
            for (StaffMember s : chosen) { queue.remove(s); queue.addLast(s); }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("weekStart", mon.toString());
            row.put("staff", chosen.stream().map(s -> Map.of("staffId", s.getStaffId(), "fullName", s.getFullName())).toList());
            row.put("skippedOnLeave", skipped);
            if (chosen.size() < k.people()) row.put("short", k.people() - chosen.size());
            plan.add(row);
            for (StaffMember s : chosen) { DutyAssignment x = new DutyAssignment(); x.setWeekStart(mon); x.setDutyCode(k.getCode()); x.setStaff(s); toSave.add(x); }
        }
        if (Boolean.TRUE.equals(in.save())) {
            LocalDate last = first.plusWeeks(weeks - 1);
            assignmentRepository.deleteAll(assignmentRepository.findByWeekStartBetweenOrderByWeekStartAsc(first, last).stream().filter(x -> x.getDutyCode().equals(k.getCode())).toList());
            assignmentRepository.saveAll(toSave);
        }
        return Map.of("duty", k.getName(), "plan", plan, "saved", Boolean.TRUE.equals(in.save()),
                "message", Boolean.TRUE.equals(in.save()) ? k.getName() + " rota saved for " + weeks + " week(s)." : "Check the rota, then save it.");
    }

    @Transactional
    public Map<String, Object> assign(AssignIn in) {
        DutyKind k = kind(in.dutyCode());
        if (in.weekStart() == null) throw bad("Choose the week.");
        StaffMember s = staffRepository.findById(in.staffId() == null ? -1L : in.staffId()).orElseThrow(() -> bad("Choose the staff member."));
        if (!s.isActive()) throw bad(s.getFullName() + " no longer works here.");
        LocalDate mon = monday(in.weekStart());
        if (assignmentRepository.findByWeekStart(mon).stream().anyMatch(x -> x.getDutyCode().equals(k.getCode()) && x.getStaff().getStaffId().equals(s.getStaffId()))) {
            throw bad(s.getFullName() + " is already on " + k.getName().toLowerCase() + " that week.");
        }
        DutyAssignment x = new DutyAssignment();
        x.setWeekStart(mon); x.setDutyCode(k.getCode()); x.setStaff(s);
        x.setNote(in.note() == null || in.note().isBlank() ? null : in.note().trim());
        assignmentRepository.save(x);
        return Map.of("message", s.getFullName() + " added to " + k.getName().toLowerCase() + " for the week of " + mon + ".");
    }

    @Transactional
    public Map<String, Object> remove(Long id) {
        DutyAssignment x = assignmentRepository.findById(id).orElseThrow(() -> bad("Not found."));
        assignmentRepository.delete(x);
        return Map.of("message", x.getStaff().getFullName() + " removed from that week's duty.");
    }

    /** Who is on each duty in the week of a date (e.g. the teacher on duty for the daily record). */
    @Transactional
    public Map<String, Object> onDate(LocalDate date) {
        seed();
        Map<String, List<String>> byDuty = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        kindRepository.findAll().forEach(k -> names.put(k.getCode(), k.getName()));
        for (DutyAssignment x : assignmentRepository.findByWeekStart(monday(date))) {
            byDuty.computeIfAbsent(names.getOrDefault(x.getDutyCode(), x.getDutyCode()), k -> new ArrayList<>()).add(x.getStaff().getFullName());
        }
        List<String> tod = assignmentRepository.findByWeekStart(monday(date)).stream().filter(x -> x.getDutyCode().equals("TOD")).map(x -> x.getStaff().getFullName()).toList();
        return Map.of("date", date.toString(), "duties", byDuty, "teacherOnDuty", String.join(" & ", tod));
    }

    /** A staff member's own duties: this week and the next 8 weeks. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(String username) {
        User u = userRepository.findByUsername(username).orElse(null);
        Long teacherId = u != null && u.getTeacher() != null ? u.getTeacher().getTeacherId() : null;
        if (teacherId == null) return List.of();
        Map<String, String> names = new HashMap<>();
        kindRepository.findAll().forEach(k -> names.put(k.getCode(), k.getName()));
        LocalDate from = schoolWeek(LocalDate.now());
        return assignmentRepository.findByWeekStartBetweenOrderByWeekStartAsc(from, from.plusWeeks(8)).stream()
                .filter(x -> teacherId.equals(x.getStaff().getTeacherId()))
                .map(x -> Map.<String, Object>of("weekStart", x.getWeekStart().toString(), "weekEnd", x.getWeekStart().plusDays(4).toString(), "duty", names.getOrDefault(x.getDutyCode(), x.getDutyCode())))
                .toList();
    }

    /** Staff on approved full-day leave for 3 or more of the week's 5 school days. */
    private Set<Long> awayMostOfWeek(LocalDate mon) {
        LocalDate fri = mon.plusDays(4);
        Map<Long, Integer> days = new HashMap<>();
        for (LeaveRequest r : leaveRepository.findByEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAsc(mon, fri)) {
            if (!"APPROVED".equals(r.getStatus()) || r.getHours() != null) continue;      // hours-only time off doesn't count
            LocalDate s = r.getStartDate().isBefore(mon) ? mon : r.getStartDate(), e = r.getEndDate().isAfter(fri) ? fri : r.getEndDate();
            int n = 0;
            for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) n++;
            days.merge(r.getStaff().getStaffId(), n, Integer::sum);
        }
        Set<Long> out = new HashSet<>();
        days.forEach((id, n) -> { if (n >= 3) out.add(id); });
        return out;
    }

    private void seed() {
        try (InputStream in = new ClassPathResource("default-settings.json").getInputStream()) {
            JsonNode duty = JSON.readTree(in).get("duty");
            if (duty == null || !duty.has("kinds")) return;
            for (JsonNode n : duty.get("kinds")) {
                if (kindRepository.findById(n.get("code").asText()).isPresent()) continue;
                DutyKind k = new DutyKind();
                k.setCode(n.get("code").asText());
                k.setName(n.get("name").asText());
                k.setPeoplePerWeek(n.has("peoplePerWeek") ? n.get("peoplePerWeek").asInt() : 1);
                k.setTeachersOnly(!n.has("teachersOnly") || n.get("teachersOnly").asBoolean());
                k.setActive(true);
                k.setSortOrder(n.has("sortOrder") ? n.get("sortOrder").asInt() : 50);
                k.setNotes(n.hasNonNull("notes") ? n.get("notes").asText() : null);
                kindRepository.save(k);
            }
        } catch (Exception ignored) { /* starting values are optional */ }
    }

    private DutyKind kind(String code) {
        seed();
        return kindRepository.findById(code == null ? "" : code.trim().toUpperCase()).orElseThrow(() -> bad("Choose the duty."));
    }

    private static Map<String, Object> kindMap(DutyKind k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", k.getCode()); m.put("name", k.getName()); m.put("peoplePerWeek", k.people());
        m.put("teachersOnly", k.onlyTeachers()); m.put("active", k.isActive()); m.put("sortOrder", k.getSortOrder()); m.put("notes", k.getNotes());
        return m;
    }

    private static Map<String, Object> assignmentMap(DutyAssignment x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assignmentId", x.getAssignmentId()); m.put("staffId", x.getStaff().getStaffId()); m.put("fullName", x.getStaff().getFullName()); m.put("note", x.getNote());
        return m;
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
