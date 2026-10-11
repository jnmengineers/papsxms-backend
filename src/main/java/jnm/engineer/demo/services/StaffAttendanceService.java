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
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Staff attendance: the office marks the daily register (approved leave appears by itself; LATE is
 * set when someone arrives after the reporting time), staff with a login can sign in and out, and
 * a monthly summary counts working days (weekends and public holidays left out).
 */
@Service
@RequiredArgsConstructor
public class StaffAttendanceService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> STATUSES = Set.of("PRESENT", "LATE", "ABSENT", "ON_LEAVE", "OFF");
    private final StaffAttendanceRepository attendanceRepository;
    private final StaffMemberRepository staffRepository;
    private final LeaveRequestRepository leaveRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveService leaveService;
    private final UserRepository userRepository;

    public record RowIn(Long staffId, String status, LocalTime timeIn, LocalTime timeOut, String note) {}
    public record DayIn(LocalDate date, List<RowIn> rows) {}

    /** The reporting time (default-settings.json → "staffAttendance" → "lateAfter"). */
    LocalTime lateAfter() {
        try (InputStream in = new ClassPathResource("default-settings.json").getInputStream()) {
            JsonNode n = JSON.readTree(in).get("staffAttendance");
            return LocalTime.parse(n.get("lateAfter").asText());
        } catch (Exception e) { return LocalTime.of(7, 45); }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> day(LocalDate date) {
        if (date == null) date = LocalDate.now();
        Map<Long, StaffAttendance> saved = new HashMap<>();
        attendanceRepository.findByDay(date).forEach(a -> saved.put(a.getStaff().getStaffId(), a));
        Map<Long, String> leave = leaveOn(date);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (StaffMember s : staffOn(date)) {
            StaffAttendance a = saved.get(s.getStaffId());
            String onLeave = leave.get(s.getStaffId());
            boolean fullDayLeave = onLeave != null && !onLeave.startsWith("Time off");
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("staffId", s.getStaffId());
            r.put("fullName", s.getFullName());
            r.put("staffNumber", s.getStaffNumber());
            r.put("jobTitle", s.getJobTitle());
            String status = a != null ? a.getStatus() : (fullDayLeave ? "ON_LEAVE" : null);
            r.put("status", status);
            r.put("timeIn", a != null && a.getTimeIn() != null ? a.getTimeIn().toString() : null);
            r.put("timeOut", a != null && a.getTimeOut() != null ? a.getTimeOut().toString() : null);
            r.put("note", a != null ? a.getNote() : null);
            r.put("leave", onLeave);
            r.put("recorded", a != null);
            r.put("recordedBy", a != null ? a.getRecordedBy() : null);
            rows.add(r);
            counts.merge(status == null ? "NOT_MARKED" : status, 1, Integer::sum);
        }
        String holiday = leaveService.holidays(date.getYear()).get(date);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", date.toString());
        out.put("lateAfter", lateAfter().toString());
        out.put("weekend", date.getDayOfWeek().getValue() >= 6);
        out.put("holiday", holiday);
        out.put("rows", rows);
        out.put("counts", counts);
        return out;
    }

    @Transactional
    public Map<String, Object> saveDay(DayIn in, String who) {
        if (in == null || in.date() == null) throw bad("Choose the date.");
        if (in.date().isAfter(LocalDate.now())) throw bad("You can't mark a day that hasn't come yet.");
        LocalTime late = lateAfter();
        int n = 0;
        for (RowIn r : in.rows() == null ? List.<RowIn>of() : in.rows()) {
            if (r.status() == null || r.status().isBlank()) continue;                  // not marked
            String st = r.status().trim().toUpperCase();
            if (!STATUSES.contains(st)) throw bad("Unknown status " + r.status() + ".");
            StaffMember s = staffRepository.findById(r.staffId() == null ? -1L : r.staffId()).orElseThrow(() -> bad("A staff member was not found."));
            if (r.timeIn() != null && r.timeOut() != null && !r.timeOut().isAfter(r.timeIn())) throw bad(s.getFullName() + ": the time out must be after the time in.");
            if (st.equals("PRESENT") && r.timeIn() != null && r.timeIn().isAfter(late)) st = "LATE";       // arrived after the reporting time
            StaffAttendance a = attendanceRepository.findByStaffStaffIdAndDay(s.getStaffId(), in.date()).orElseGet(StaffAttendance::new);
            a.setStaff(s);
            a.setDay(in.date());
            a.setStatus(st);
            a.setTimeIn(st.equals("ABSENT") || st.equals("ON_LEAVE") || st.equals("OFF") ? null : r.timeIn());
            a.setTimeOut(st.equals("ABSENT") || st.equals("ON_LEAVE") || st.equals("OFF") ? null : r.timeOut());
            a.setNote(r.note() == null || r.note().isBlank() ? null : (r.note().trim().length() > 150 ? r.note().trim().substring(0, 150) : r.note().trim()));
            a.setRecordedBy(who);
            a.setRecordedAt(LocalDateTime.now());
            attendanceRepository.save(a);
            n++;
        }
        return Map.of("message", n + " staff marked for " + in.date() + ".");
    }

    /** Sign in now (the server's clock), for a staff member whose login is linked to their record. */
    @Transactional
    public Map<String, Object> signIn(String username) {
        StaffMember s = staffOf(username);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        StaffAttendance a = attendanceRepository.findByStaffStaffIdAndDay(s.getStaffId(), today).orElseGet(StaffAttendance::new);
        if (a.getTimeIn() != null) throw bad("You already signed in today at " + a.getTimeIn() + ".");
        a.setStaff(s);
        a.setDay(today);
        a.setStatus(now.isAfter(lateAfter()) ? "LATE" : "PRESENT");
        a.setTimeIn(now);
        a.setRecordedBy(username);
        a.setRecordedAt(LocalDateTime.now());
        attendanceRepository.save(a);
        return Map.of("message", "Signed in at " + now + (a.getStatus().equals("LATE") ? " (after " + lateAfter() + " — marked late)." : "."));
    }

    @Transactional
    public Map<String, Object> signOut(String username) {
        StaffMember s = staffOf(username);
        StaffAttendance a = attendanceRepository.findByStaffStaffIdAndDay(s.getStaffId(), LocalDate.now()).orElse(null);
        if (a == null || a.getTimeIn() == null) throw bad("Sign in first.");
        if (a.getTimeOut() != null) throw bad("You already signed out today at " + a.getTimeOut() + ".");
        LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        a.setTimeOut(now.isAfter(a.getTimeIn()) ? now : a.getTimeIn().plusMinutes(1));
        attendanceRepository.save(a);
        return Map.of("message", "Signed out at " + a.getTimeOut() + ". Have a good evening.");
    }

    /** Today's sign-in for the logged-in staff member (null if their login isn't linked). */
    @Transactional(readOnly = true)
    public Map<String, Object> mine(String username) {
        StaffMember s = staffOfOrNull(username);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("linked", s != null);
        if (s == null) return m;
        StaffAttendance a = attendanceRepository.findByStaffStaffIdAndDay(s.getStaffId(), LocalDate.now()).orElse(null);
        m.put("status", a != null ? a.getStatus() : null);
        m.put("timeIn", a != null && a.getTimeIn() != null ? a.getTimeIn().toString() : null);
        m.put("timeOut", a != null && a.getTimeOut() != null ? a.getTimeOut().toString() : null);
        m.put("lateAfter", lateAfter().toString());
        return m;
    }

    /** Monthly summary per staff member over working days (Mon–Fri, not public holidays), up to today. */
    @Transactional(readOnly = true)
    public Map<String, Object> month(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1), to = ym.atEndOfMonth().isAfter(LocalDate.now()) ? LocalDate.now() : ym.atEndOfMonth();
        Map<LocalDate, String> hol = leaveService.holidays(year);
        List<LocalDate> workDays = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) if (d.getDayOfWeek().getValue() < 6 && !hol.containsKey(d)) workDays.add(d);
        Map<Long, Map<LocalDate, String>> marks = new HashMap<>();
        if (!to.isBefore(from)) attendanceRepository.findByDayBetween(from, to).forEach(a -> marks.computeIfAbsent(a.getStaff().getStaffId(), k -> new HashMap<>()).put(a.getDay(), a.getStatus()));
        Map<Long, Set<LocalDate>> leaveDays = new HashMap<>();
        if (!to.isBefore(from)) for (LeaveRequest r : leaveRepository.findByEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAsc(from, to)) {
            if (!"APPROVED".equals(r.getStatus()) || r.getHours() != null) continue;
            for (LocalDate d = r.getStartDate(); !d.isAfter(r.getEndDate()); d = d.plusDays(1)) leaveDays.computeIfAbsent(r.getStaff().getStaffId(), k -> new HashSet<>()).add(d);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StaffMember s : staffRepository.findByActiveTrueOrderByFullNameAsc()) {
            Map<String, Integer> c = new LinkedHashMap<>();
            for (String k : List.of("PRESENT", "LATE", "ABSENT", "ON_LEAVE", "OFF", "NOT_MARKED")) c.put(k, 0);
            int expected = 0;
            for (LocalDate d : workDays) {
                if (s.getStartDate() != null && d.isBefore(s.getStartDate())) continue;
                expected++;
                String st = marks.getOrDefault(s.getStaffId(), Map.of()).get(d);
                if (st == null && leaveDays.getOrDefault(s.getStaffId(), Set.of()).contains(d)) st = "ON_LEAVE";
                c.merge(st == null ? "NOT_MARKED" : st, 1, Integer::sum);
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("staffId", s.getStaffId()); r.put("fullName", s.getFullName()); r.put("jobTitle", s.getJobTitle());
            r.put("workingDays", expected); r.put("counts", c);
            rows.add(r);
        }
        return Map.of("year", year, "month", month, "workingDays", workDays.size(), "rows", rows, "to", to.toString());
    }

    /** Active staff who had started by that day. */
    private List<StaffMember> staffOn(LocalDate date) {
        return staffRepository.findByActiveTrueOrderByFullNameAsc().stream().filter(s -> s.getStartDate() == null || !s.getStartDate().isAfter(date)).toList();
    }

    /** staffId → what approved leave they have that day ("Annual leave", or "Time off 10:00–13:30"). */
    private Map<Long, String> leaveOn(LocalDate date) {
        Map<String, String> names = new HashMap<>();
        leaveTypeRepository.findAll().forEach(t -> names.put(t.getCode(), t.getName()));
        Map<Long, String> out = new HashMap<>();
        for (LeaveRequest r : leaveRepository.findByEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAsc(date, date)) {
            if (!"APPROVED".equals(r.getStatus())) continue;
            out.put(r.getStaff().getStaffId(), r.getHours() != null ? "Time off " + r.getStartTime() + "–" + r.getEndTime() : names.getOrDefault(r.getTypeCode(), r.getTypeCode()));
        }
        return out;
    }

    private StaffMember staffOfOrNull(String username) {
        User u = username == null ? null : userRepository.findByUsername(username).orElse(null);
        Long teacherId = u != null && u.getTeacher() != null ? u.getTeacher().getTeacherId() : null;
        if (teacherId == null) return null;
        return staffRepository.findAll().stream().filter(s -> teacherId.equals(s.getTeacherId()) && s.isActive()).findFirst().orElse(null);
    }

    private StaffMember staffOf(String username) {
        StaffMember s = staffOfOrNull(username);
        if (s == null) throw bad("Your login isn't linked to a staff record — ask the office to add you on the Staff page.");
        return s;
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
