package jnm.engineer.demo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jnm.engineer.demo.models.LeaveHoliday;
import jnm.engineer.demo.models.LeaveRequest;
import jnm.engineer.demo.models.LeaveType;
import jnm.engineer.demo.models.StaffMember;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.LeaveHolidayRepository;
import jnm.engineer.demo.repositories.LeaveRequestRepository;
import jnm.engineer.demo.repositories.LeaveTypeRepository;
import jnm.engineer.demo.repositories.StaffMemberRepository;
import jnm.engineer.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.*;

/**
 * Staff leave: leave types (rules), public holidays, applications and approval, balances.
 * Leave types start from default-settings.json → "leave" and are editable. A leave year is a
 * calendar year; working-day leave skips weekends and public holidays.
 */
@Service
@RequiredArgsConstructor
public class LeaveService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LeaveTypeRepository typeRepository;
    private final LeaveRequestRepository requestRepository;
    private final LeaveHolidayRepository holidayRepository;
    private final StaffMemberRepository staffRepository;
    private final UserRepository userRepository;

    /** startTime/endTime: short permission (hours on one day). otherName: what "Other" leave is for. */
    public record LeaveIn(Long staffId, String typeCode, LocalDate startDate, LocalDate endDate, String reason, Long reliefStaffId,
                          String contactWhileAway, Boolean approveNow, java.time.LocalTime startTime, java.time.LocalTime endTime, String otherName) {}
    public record TypeIn(String code, String name, Integer daysPerYear, Boolean workingDays, Boolean paid, String genderOnly,
                         Integer carryOverMax, Boolean proRata, Boolean active, Integer sortOrder, String notes, Boolean byHours, Boolean specify) {}
    public record HolidayIn(LocalDate date, String name) {}

    // ══ Leave types ═════════════════════════════════════════════════════════
    @Transactional
    public List<Map<String, Object>> types() {
        seedTypes();
        return typeRepository.findAllByOrderBySortOrderAscNameAsc().stream().map(LeaveService::typeMap).toList();
    }

    @Transactional
    public Map<String, Object> saveType(TypeIn in) {
        String code = in.code() == null ? "" : in.code().trim().toUpperCase().replaceAll("[^A-Z0-9_]", "");
        if (code.length() < 2) throw bad("Enter a short code (e.g. STUDY).");
        String name = trim(in.name(), 60);
        if (name == null) throw bad("Enter the name of the leave.");
        if (in.daysPerYear() != null && (in.daysPerYear() < 0 || in.daysPerYear() > 366)) throw bad("Days per year must be between 0 and 366.");
        String g = in.genderOnly() == null || in.genderOnly().isBlank() ? null : in.genderOnly().trim().toUpperCase();
        if (g != null && !g.equals("FEMALE") && !g.equals("MALE")) throw bad("Only for: women, men, or everyone.");
        LeaveType t = typeRepository.findById(code).orElseGet(LeaveType::new);
        t.setCode(code);
        t.setName(name);
        t.setDaysPerYear(in.daysPerYear());
        t.setWorkingDays(in.workingDays() == null || in.workingDays());
        t.setPaid(in.paid() == null || in.paid());
        t.setGenderOnly(g);
        t.setCarryOverMax(Math.max(0, in.carryOverMax() == null ? 0 : in.carryOverMax()));
        t.setProRata(Boolean.TRUE.equals(in.proRata()));
        t.setActive(in.active() == null || in.active());
        t.setSortOrder(in.sortOrder() == null ? 99 : in.sortOrder());
        t.setNotes(trim(in.notes(), 200));
        t.setByHours(Boolean.TRUE.equals(in.byHours()));
        t.setSpecify(Boolean.TRUE.equals(in.specify()));
        if (t.isByHours() && t.getDaysPerYear() != null) throw bad("Leave counted in hours has no yearly day limit — leave \"days a year\" blank.");
        typeRepository.save(t);
        return Map.of("message", name + " saved.");
    }

    private volatile boolean seeded = false;

    /** Adds every kind of leave in default-settings.json that the school doesn't have yet (never changes existing ones). */
    private void seedTypes() {
        if (seeded) return;
        JsonNode leave = settings();
        if (leave == null || !leave.has("types")) return;
        for (JsonNode n : leave.get("types")) {
            if (typeRepository.findById(n.get("code").asText()).isPresent()) continue;
            LeaveType t = new LeaveType();
            t.setCode(n.get("code").asText());
            t.setName(n.get("name").asText());
            t.setDaysPerYear(n.hasNonNull("daysPerYear") ? n.get("daysPerYear").asInt() : null);
            t.setWorkingDays(!n.has("workingDays") || n.get("workingDays").asBoolean());
            t.setPaid(!n.has("paid") || n.get("paid").asBoolean());
            t.setGenderOnly(n.hasNonNull("genderOnly") ? n.get("genderOnly").asText() : null);
            t.setCarryOverMax(n.has("carryOverMax") ? n.get("carryOverMax").asInt() : 0);
            t.setProRata(n.has("proRata") && n.get("proRata").asBoolean());
            t.setActive(true);
            t.setSortOrder(n.has("sortOrder") ? n.get("sortOrder").asInt() : 99);
            t.setNotes(n.hasNonNull("notes") ? trim(n.get("notes").asText(), 200) : null);
            t.setByHours(n.has("byHours") && n.get("byHours").asBoolean());
            t.setSpecify(n.has("specify") && n.get("specify").asBoolean());
            typeRepository.save(t);
        }
        seeded = true;
    }

    private static JsonNode settings() {
        try (InputStream in = new ClassPathResource("default-settings.json").getInputStream()) { return JSON.readTree(in).get("leave"); }
        catch (Exception e) { return null; }
    }

    // ══ Public holidays ═════════════════════════════════════════════════════
    /** Holidays in a year: fixed ones + Easter (automatic) and the ones the admin added. date → name */
    @Transactional(readOnly = true)
    public Map<LocalDate, String> holidays(int year) {
        Map<LocalDate, String> out = new TreeMap<>();
        JsonNode leave = settings();
        if (leave != null && leave.has("fixedHolidays")) {
            for (JsonNode h : leave.get("fixedHolidays")) out.put(MonthDay.parse("--" + h.get("monthDay").asText()).atYear(year), h.get("name").asText());
        }
        if (leave == null || !leave.has("easter") || leave.get("easter").asBoolean()) {
            LocalDate easter = easterSunday(year);
            out.put(easter.minusDays(2), "Good Friday");
            out.put(easter.plusDays(1), "Easter Monday");
        }
        for (LeaveHoliday h : holidayRepository.findAllByOrderByHolidayDateAsc()) if (h.getHolidayDate().getYear() == year) out.put(h.getHolidayDate(), h.getName());
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> holidayList(int year) {
        Set<LocalDate> added = new HashSet<>();
        holidayRepository.findAllByOrderByHolidayDateAsc().forEach(h -> added.add(h.getHolidayDate()));
        List<Map<String, Object>> out = new ArrayList<>();
        holidays(year).forEach((d, n) -> out.add(Map.of("date", d.toString(), "name", n, "added", added.contains(d))));
        return out;
    }

    @Transactional
    public Map<String, Object> addHoliday(HolidayIn in) {
        if (in == null || in.date() == null) throw bad("Choose the date.");
        String name = trim(in.name(), 80);
        if (name == null) throw bad("Enter the name of the holiday.");
        LeaveHoliday h = new LeaveHoliday();
        h.setHolidayDate(in.date());
        h.setName(name);
        holidayRepository.save(h);
        return Map.of("message", name + " (" + in.date() + ") added.");
    }

    @Transactional
    public Map<String, Object> removeHoliday(LocalDate date) {
        holidayRepository.deleteById(date);
        return Map.of("message", "Holiday removed.");
    }

    /** Easter Sunday (Gregorian calendar, "anonymous" algorithm). */
    static LocalDate easterSunday(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(y, month, day);
    }

    // ══ Counting days ═══════════════════════════════════════════════════════
    /** Days the leave uses: Monday–Friday without public holidays, or every calendar day (type rule). */
    @Transactional(readOnly = true)
    public int countDays(LeaveType t, LocalDate start, LocalDate end) {
        if (!t.isWorkingDays()) return (int) (end.toEpochDay() - start.toEpochDay() + 1);
        Map<LocalDate, String> hol = new HashMap<>(holidays(start.getYear()));
        if (end.getYear() != start.getYear()) hol.putAll(holidays(end.getYear()));
        int n = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY || hol.containsKey(d)) continue;
            n++;
        }
        return n;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> preview(String typeCode, LocalDate start, LocalDate end) {
        LeaveType t = type(typeCode);
        if (start == null || end == null || end.isBefore(start)) throw bad("Choose the first and last day.");
        int days = countDays(t, start, end);
        List<String> skipped = new ArrayList<>();
        if (t.isWorkingDays()) {
            Map<LocalDate, String> hol = holidays(start.getYear());
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) if (hol.containsKey(d) && d.getDayOfWeek().getValue() < 6) skipped.add(hol.get(d) + " (" + d + ")");
        }
        return Map.of("days", days, "holidaysSkipped", skipped, "workingDays", t.isWorkingDays());
    }

    // ══ Balances ════════════════════════════════════════════════════════════
    /** One person's balance for one type in a year: entitled, carried over, taken, pending, left (null = no limit). */
    Map<String, Object> balance(StaffMember s, LeaveType t, int year, List<LeaveRequest> theirs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("typeCode", t.getCode());
        m.put("typeName", t.getName());
        boolean applies = t.getGenderOnly() == null || t.getGenderOnly().equalsIgnoreCase(String.valueOf(s.getGender()));
        m.put("applies", applies);
        if (t.isByHours()) {                                       // short permission: hours, no limit
            m.put("unit", "hours");
            m.put("taken", sumHours(theirs, t.getCode(), year, "APPROVED"));
            m.put("pending", sumHours(theirs, t.getCode(), year, "PENDING"));
            m.put("entitled", null); m.put("carried", 0); m.put("left", null);
            return m;
        }
        m.put("unit", "days");
        int taken = sumDays(theirs, t.getCode(), year, "APPROVED");
        int pending = sumDays(theirs, t.getCode(), year, "PENDING");
        m.put("taken", taken);
        m.put("pending", pending);
        if (t.getDaysPerYear() == null) { m.put("entitled", null); m.put("carried", 0); m.put("left", null); return m; }
        int entitled = entitlement(s, t, year);
        int carried = 0;
        if (t.getCarryOverMax() > 0) {
            int prevLeft = entitlement(s, t, year - 1) - sumDays(theirs, t.getCode(), year - 1, "APPROVED");
            carried = Math.max(0, Math.min(t.getCarryOverMax(), prevLeft));
        }
        m.put("entitled", entitled);
        m.put("carried", carried);
        m.put("left", entitled + carried - taken);
        return m;
    }

    private static int entitlement(StaffMember s, LeaveType t, int year) {
        int full = t.getDaysPerYear() == null ? 0 : t.getDaysPerYear();
        LocalDate start = s.getStartDate();
        if (start != null && start.getYear() > year) return 0;                 // hadn't joined yet
        if (t.isProRata() && start != null && start.getYear() == year) {      // joined during the year: a share
            int daysInYear = LocalDate.of(year, 12, 31).getDayOfYear();
            int served = daysInYear - start.getDayOfYear() + 1;
            return (int) Math.round(full * (double) served / daysInYear);
        }
        return full;
    }

    private static double sumHours(List<LeaveRequest> list, String type, int year, String status) {
        return list.stream().filter(r -> r.getTypeCode().equals(type) && r.getStatus().equals(status) && r.getStartDate().getYear() == year && r.getHours() != null)
                .mapToDouble(r -> r.getHours().doubleValue()).sum();
    }

    private static int sumDays(List<LeaveRequest> list, String type, int year, String status) {
        return list.stream().filter(r -> r.getTypeCode().equals(type) && r.getStatus().equals(status) && r.getStartDate().getYear() == year)
                .mapToInt(LeaveRequest::getDays).sum();
    }

    @Transactional
    public List<Map<String, Object>> balances(int year) {
        seedTypes();
        List<LeaveType> types = typeRepository.findAllByOrderBySortOrderAscNameAsc().stream().filter(LeaveType::isActive).toList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (StaffMember s : staffRepository.findByActiveTrueOrderByFullNameAsc()) {
            List<LeaveRequest> theirs = requestRepository.findByStaffStaffIdOrderByStartDateDesc(s.getStaffId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("staffId", s.getStaffId());
            row.put("staffNumber", s.getStaffNumber());
            row.put("fullName", s.getFullName());
            row.put("jobTitle", s.getJobTitle());
            row.put("balances", types.stream().map(t -> balance(s, t, year, theirs)).toList());
            out.add(row);
        }
        return out;
    }

    // ══ Applying, approving, cancelling ═════════════════════════════════════
    /** The admin records leave for someone (optionally approved at once — e.g. support staff without a login). */
    @Transactional
    public Map<String, Object> record(LeaveIn in, String who) {
        StaffMember s = staffRepository.findById(in.staffId() == null ? -1L : in.staffId()).orElseThrow(() -> bad("Choose the staff member."));
        LeaveRequest r = create(s, in, who);
        if (Boolean.TRUE.equals(in.approveNow())) { r.setStatus("APPROVED"); r.setDecidedBy(who); r.setDecidedAt(LocalDateTime.now()); requestRepository.save(r); }
        return Map.of("message", s.getFullName() + ": " + amount(r) + " of " + label(r).toLowerCase() + (Boolean.TRUE.equals(in.approveNow()) ? " recorded and approved." : " recorded — waiting for approval."));
    }

    /** A staff member applies for themselves (their login is linked to their staff record). */
    @Transactional
    public Map<String, Object> applyMine(LeaveIn in, String username) {
        StaffMember s = staffOf(username);
        LeaveRequest r = create(s, new LeaveIn(s.getStaffId(), in.typeCode(), in.startDate(), in.endDate(), in.reason(), in.reliefStaffId(), in.contactWhileAway(), false,
                in.startTime(), in.endTime(), in.otherName()), username);
        return Map.of("message", "Application sent: " + amount(r) + " of " + label(r).toLowerCase() + " — waiting for approval.");
    }

    private LeaveRequest create(StaffMember s, LeaveIn in, String who) {
        if (!s.isActive()) throw bad(s.getFullName() + " no longer works here.");
        LeaveType t = type(in.typeCode());
        if (!t.isActive()) throw bad(t.getName() + " is switched off.");
        if (t.getGenderOnly() != null) {
            if (s.getGender() == null) throw bad("Record " + s.getFullName() + "'s gender on the staff record first (" + t.getName() + " is only for " + (t.getGenderOnly().equals("FEMALE") ? "women" : "men") + ").");
            if (!t.getGenderOnly().equalsIgnoreCase(s.getGender())) throw bad(t.getName() + " is only for " + (t.getGenderOnly().equals("FEMALE") ? "women" : "men") + ".");
        }
        LocalDate start = in.startDate();
        LocalDate end = t.isByHours() && in.endDate() == null ? start : in.endDate();   // hours = one day; the last day is optional
        if (start == null || end == null) throw bad(t.isByHours() ? "Choose the day." : "Choose the first and last day of the leave.");
        if (end.isBefore(start)) throw bad("The last day can't be before the first day.");
        if (start.getYear() != end.getYear()) throw bad("Leave can't run into the next year — split it into two applications (one per year).");
        if (start.isAfter(LocalDate.now().plusYears(1))) throw bad("That is more than a year away.");
        String other = trim(in.otherName(), 80);
        if (t.isSpecify() && (other == null || other.length() < 3)) throw bad("Say what this leave is for (e.g. a family wedding, an exam).");
        java.math.BigDecimal hours = null;
        int days;
        if (t.isByHours()) {                                       // a few hours on ONE day
            if (!start.equals(end)) throw bad("A short permission is for one day — choose the same first and last day.");
            if (in.startTime() == null || in.endTime() == null) throw bad("Choose the time out and the time back.");
            if (!in.endTime().isAfter(in.startTime())) throw bad("The time back must be after the time out.");
            long minutes = java.time.Duration.between(in.startTime(), in.endTime()).toMinutes();
            if (minutes < 15) throw bad("A permission is at least 15 minutes.");
            hours = java.math.BigDecimal.valueOf(Math.round(minutes / 15.0) / 4.0).setScale(2, java.math.RoundingMode.HALF_UP);   // to the quarter hour
            days = 0;
        } else {
            days = countDays(t, start, end);
            if (days <= 0) throw bad("Those dates are all weekends or public holidays — no leave days are needed.");
        }
        List<LeaveRequest> theirs = requestRepository.findByStaffStaffIdOrderByStartDateDesc(s.getStaffId());
        for (LeaveRequest o : theirs) {
            if (!(o.getStatus().equals("PENDING") || o.getStatus().equals("APPROVED")) || o.getStartDate().isAfter(end) || o.getEndDate().isBefore(start)) continue;
            boolean bothHours = t.isByHours() && o.getStartTime() != null && o.getEndTime() != null;
            if (bothHours && !(in.startTime().isBefore(o.getEndTime()) && o.getStartTime().isBefore(in.endTime()))) continue;   // different times the same day
            throw bad("This overlaps " + s.getFullName() + "'s " + label(o).toLowerCase() + " on " + o.getStartDate()
                    + (o.getStartTime() != null ? " " + o.getStartTime() + "–" + o.getEndTime() : " to " + o.getEndDate()) + " (" + o.getStatus().toLowerCase() + ").");
        }
        if (t.getDaysPerYear() != null) {
            Map<String, Object> b = balance(s, t, start.getYear(), theirs);
            int left = (Integer) b.get("left"), pending = (Integer) b.get("pending");
            if (days > left - pending) {
                throw bad(s.getFullName() + " has " + Math.max(0, left - pending) + " day(s) of " + t.getName().toLowerCase() + " left in " + start.getYear()
                        + (pending > 0 ? " (after " + pending + " day(s) already waiting for approval)" : "") + " — this needs " + days + ".");
            }
        }
        if (in.reliefStaffId() != null && in.reliefStaffId().equals(s.getStaffId())) throw bad("Someone else must cover their work.");
        LeaveRequest r = new LeaveRequest();
        r.setStaff(s);
        r.setTypeCode(t.getCode());
        r.setStartDate(start);
        r.setEndDate(end);
        r.setDays(days);
        r.setHours(hours);
        r.setStartTime(t.isByHours() ? in.startTime() : null);
        r.setEndTime(t.isByHours() ? in.endTime() : null);
        r.setOtherName(t.isSpecify() ? other : null);
        r.setReason(trim(in.reason(), 300));
        r.setReliefStaffId(in.reliefStaffId());
        r.setContactWhileAway(trim(in.contactWhileAway(), 100));
        r.setStatus("PENDING");
        r.setAppliedBy(who);
        r.setAppliedAt(LocalDateTime.now());
        return requestRepository.save(r);
    }

    @Transactional
    public Map<String, Object> decide(Long id, boolean approve, String note, String who) { return decide(id, approve, note, null, who); }

    /** otherName: for "Other" leave, HR confirms or specifies what it is recorded as. */
    @Transactional
    public Map<String, Object> decide(Long id, boolean approve, String note, String otherName, String who) {
        LeaveRequest r = requestRepository.findById(id).orElseThrow(() -> notFound("Leave application not found."));
        if (!r.getStatus().equals("PENDING")) throw bad("This application is already " + r.getStatus().toLowerCase() + ".");
        StaffMember approver = staffOfOrNull(who);
        if (approver != null && approver.getStaffId().equals(r.getStaff().getStaffId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't approve your own leave — someone else (the head teacher or an admin) must.");
        }
        String n = trim(note, 200);
        if (!approve && (n == null || n.length() < 3)) throw bad("Say why the leave is not approved.");
        if (approve) {
            LeaveType t = type(r.getTypeCode());
            if (t.getDaysPerYear() != null) {     // check again: other leave may have been approved since they applied
                List<LeaveRequest> others = requestRepository.findByStaffStaffIdOrderByStartDateDesc(r.getStaff().getStaffId()).stream().filter(o -> !o.getLeaveId().equals(id)).toList();
                int left = (Integer) balance(r.getStaff(), t, r.getStartDate().getYear(), others).get("left");
                if (r.getDays() > left) throw bad(r.getStaff().getFullName() + " only has " + Math.max(0, left) + " day(s) left now — this needs " + r.getDays() + ".");
            }
        }
        String spec = trim(otherName, 80);
        if (spec != null && type(r.getTypeCode()).isSpecify()) r.setOtherName(spec);
        r.setStatus(approve ? "APPROVED" : "REJECTED");
        r.setDecidedBy(who);
        r.setDecidedAt(LocalDateTime.now());
        r.setDecisionNote(n);
        requestRepository.save(r);
        return Map.of("message", r.getStaff().getFullName() + "'s leave " + (approve ? "approved." : "not approved."));
    }

    /** Owner: a pending application, or approved leave that hasn't started. Admin: any pending/approved leave. */
    @Transactional
    public Map<String, Object> cancel(Long id, String who, boolean admin, String note) {
        LeaveRequest r = requestRepository.findById(id).orElseThrow(() -> notFound("Leave application not found."));
        if (!admin) {
            StaffMember mine = staffOf(who);
            if (!r.getStaff().getStaffId().equals(mine.getStaffId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That is not your leave.");
            if (r.getStatus().equals("APPROVED") && !r.getStartDate().isAfter(LocalDate.now())) throw bad("This leave has already started — ask the office to change it.");
        }
        if (!r.getStatus().equals("PENDING") && !r.getStatus().equals("APPROVED")) throw bad("This application is already " + r.getStatus().toLowerCase() + ".");
        r.setStatus("CANCELLED");
        r.setDecidedBy(who);
        r.setDecidedAt(LocalDateTime.now());
        r.setDecisionNote(trim(note, 200));
        requestRepository.save(r);
        return Map.of("message", "Leave cancelled — the days are back in the balance.");
    }

    // ══ Lists ═══════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public List<Map<String, Object>> requests(LocalDate from, LocalDate to, String status) {
        List<LeaveRequest> list = status != null && !status.isBlank() && from == null
                ? requestRepository.findByStatusOrderByStartDateAsc(status.trim().toUpperCase())
                : requestRepository.findByEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAsc(
                        from != null ? from : LocalDate.now().withDayOfYear(1), to != null ? to : LocalDate.now().withMonth(12).withDayOfMonth(31));
        String st = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        return list.stream().filter(r -> st == null || r.getStatus().equals(st)).map(this::requestMap).toList();
    }

    /** For the Staff → Leave front page: who is away today, coming up in 14 days, waiting for approval. */
    @Transactional(readOnly = true)
    public Map<String, Object> today() {
        LocalDate t = LocalDate.now();
        List<Map<String, Object>> away = new ArrayList<>(), soon = new ArrayList<>();
        for (LeaveRequest r : requestRepository.findByEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAsc(t, t.plusDays(14))) {
            if (!r.getStatus().equals("APPROVED")) continue;
            (r.getStartDate().isAfter(t) ? soon : away).add(requestMap(r));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("onLeaveToday", away);
        m.put("upcoming", soon);
        m.put("pending", requestRepository.findByStatusOrderByStartDateAsc("PENDING").stream().map(this::requestMap).toList());
        return m;
    }

    /** "My Leave": the logged-in staff member's balances and applications. */
    @Transactional
    public Map<String, Object> mine(String username, int year) {
        seedTypes();
        StaffMember s = staffOf(username);
        List<LeaveRequest> theirs = requestRepository.findByStaffStaffIdOrderByStartDateDesc(s.getStaffId());
        List<LeaveType> types = typeRepository.findAllByOrderBySortOrderAscNameAsc().stream().filter(LeaveType::isActive).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("staffId", s.getStaffId());
        m.put("fullName", s.getFullName());
        m.put("year", year);
        m.put("types", types.stream().filter(t -> t.getGenderOnly() == null || t.getGenderOnly().equalsIgnoreCase(String.valueOf(s.getGender()))).map(LeaveService::typeMap).toList());
        m.put("balances", types.stream().map(t -> balance(s, t, year, theirs)).filter(b -> Boolean.TRUE.equals(b.get("applies"))).toList());
        m.put("requests", theirs.stream().map(this::requestMap).toList());
        m.put("colleagues", staffRepository.findByActiveTrueOrderByFullNameAsc().stream().filter(x -> !x.getStaffId().equals(s.getStaffId()))
                .map(x -> Map.<String, Object>of("staffId", x.getStaffId(), "fullName", x.getFullName())).toList());
        return m;
    }

    /** The staff record linked to a login, or null (e.g. an admin login that isn't a teacher). */
    private StaffMember staffOfOrNull(String username) {
        if (username == null) return null;
        User u = userRepository.findByUsername(username).orElse(null);
        Long teacherId = u != null && u.getTeacher() != null ? u.getTeacher().getTeacherId() : null;
        if (teacherId == null) return null;
        return staffRepository.findAll().stream().filter(s -> teacherId.equals(s.getTeacherId())).findFirst().orElse(null);
    }

    private StaffMember staffOf(String username) {
        User u = userRepository.findByUsername(username).orElseThrow(() -> notFound("Login not found."));
        Long teacherId = u.getTeacher() != null ? u.getTeacher().getTeacherId() : null;
        return staffRepository.findAll().stream().filter(s -> teacherId != null && teacherId.equals(s.getTeacherId())).findFirst()
                .orElseThrow(() -> bad("Your login isn't linked to a staff record yet — ask the office to add you on the Staff page."));
    }

    private LeaveType type(String code) {
        seedTypes();
        return typeRepository.findById(code == null ? "" : code.trim().toUpperCase()).orElseThrow(() -> bad("Choose the kind of leave."));
    }

    private Map<String, Object> requestMap(LeaveRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("leaveId", r.getLeaveId());
        m.put("staffId", r.getStaff().getStaffId());
        m.put("fullName", r.getStaff().getFullName());
        m.put("staffNumber", r.getStaff().getStaffNumber());
        m.put("jobTitle", r.getStaff().getJobTitle());
        m.put("typeCode", r.getTypeCode());
        m.put("typeName", typeRepository.findById(r.getTypeCode()).map(LeaveType::getName).orElse(r.getTypeCode()));
        m.put("startDate", r.getStartDate().toString());
        m.put("endDate", r.getEndDate().toString());
        m.put("days", r.getDays());
        m.put("hours", r.getHours());
        m.put("startTime", r.getStartTime() != null ? r.getStartTime().toString() : null);
        m.put("endTime", r.getEndTime() != null ? r.getEndTime().toString() : null);
        m.put("otherName", r.getOtherName());
        m.put("label", label(r));                 // "Other: Sitting a KNEC exam" / "Annual leave"
        m.put("amount", amount(r));               // "10 day(s)" / "2.5 hour(s)"
        m.put("reason", r.getReason());
        m.put("reliefStaffId", r.getReliefStaffId());
        m.put("reliefName", r.getReliefStaffId() == null ? null : staffRepository.findById(r.getReliefStaffId()).map(StaffMember::getFullName).orElse(null));
        m.put("contactWhileAway", r.getContactWhileAway());
        m.put("status", r.getStatus());
        m.put("appliedBy", r.getAppliedBy());
        m.put("appliedAt", r.getAppliedAt() != null ? r.getAppliedAt().toString() : null);
        m.put("decidedBy", r.getDecidedBy());
        m.put("decisionNote", r.getDecisionNote());
        return m;
    }

    private static Map<String, Object> typeMap(LeaveType t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("daysPerYear", t.getDaysPerYear());
        m.put("workingDays", t.isWorkingDays());
        m.put("paid", t.isPaid());
        m.put("genderOnly", t.getGenderOnly());
        m.put("carryOverMax", t.getCarryOverMax());
        m.put("proRata", t.isProRata());
        m.put("active", t.isActive());
        m.put("sortOrder", t.getSortOrder());
        m.put("notes", t.getNotes());
        m.put("byHours", t.isByHours());
        m.put("specify", t.isSpecify());
        return m;
    }

    /** What the leave is called: "Other: <what HR specified>" or the kind's name. */
    private String label(LeaveRequest r) {
        String name = typeRepository.findById(r.getTypeCode()).map(LeaveType::getName).orElse(r.getTypeCode());
        return r.getOtherName() != null ? "Other: " + r.getOtherName() : name;
    }

    private static String amount(LeaveRequest r) {
        if (r.getHours() != null) return r.getHours().stripTrailingZeros().toPlainString() + " hour(s) (" + r.getStartTime() + "–" + r.getEndTime() + ")";
        return r.getDays() + " day(s)";
    }

    private static String trim(String v, int max) { if (v == null || v.isBlank()) return null; String t = v.trim(); return t.length() > max ? t.substring(0, max) : t; }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
}
