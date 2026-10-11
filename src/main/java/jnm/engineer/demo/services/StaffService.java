package jnm.engineer.demo.services;

import jnm.engineer.demo.models.StaffMember;
import jnm.engineer.demo.models.StaffQualification;
import jnm.engineer.demo.models.Teacher;
import jnm.engineer.demo.repositories.StaffMemberRepository;
import jnm.engineer.demo.repositories.StaffQualificationRepository;
import jnm.engineer.demo.repositories.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Staff records: one register for everyone working at the school — teachers and support staff
 * (a private school employs its own staff). Payroll uses the same records (those "on the school payroll").
 */
@Service
@RequiredArgsConstructor
public class StaffService {
    private static final Set<String> CATEGORIES = Set.of("TEACHING", "SUPPORT", "ADMINISTRATION");
    /** A private school employs its own staff; AGENCY = outsourced (e.g. security guards paid by their firm). */
    private static final Set<String> EMPLOYERS = Set.of("SCHOOL", "AGENCY", "OTHER");
    private static final Set<String> TERMS = Set.of("PERMANENT", "CONTRACT", "CASUAL", "INTERN", "VOLUNTEER");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE");
    private static final Set<String> LEVELS = Set.of("PHD", "MASTERS", "DEGREE", "DIPLOMA", "CERTIFICATE", "KCSE", "OTHER");

    private final StaffMemberRepository staffRepository;
    private final StaffQualificationRepository qualificationRepository;
    private final TeacherRepository teacherRepository;

    public record QualIn(String level, String title, String institution, Integer yearCompleted) {}
    public record StaffRecordIn(Long staffId, String staffNumber, String fullName, String jobTitle, String category, Long teacherId,
                                String phone, String email, String gender, LocalDate dateOfBirth, String idNumber, String residence,
                                String employer, String tscNumber, String employmentType, LocalDate startDate, LocalDate contractEnd,
                                String department, Boolean active, LocalDate dateLeft, String leftReason, Boolean onPayroll,
                                String kraPin, String nssfNumber, String shaNumber,
                                String nextOfKinName, String nextOfKinRelationship, String nextOfKinPhone, String notes,
                                List<QualIn> qualifications) {}

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (StaffMember s : staffRepository.findAllByOrderByActiveDescFullNameAsc()) {
            out.add(map(s, qualificationRepository.findByStaffStaffIdOrderByYearCompletedDesc(s.getStaffId())));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        StaffMember s = staffRepository.findById(id).orElseThrow(() -> notFound("Staff member not found."));
        return map(s, qualificationRepository.findByStaffStaffIdOrderByYearCompletedDesc(id));
    }

    @Transactional
    public Map<String, Object> save(StaffRecordIn in) {
        StaffMember s = in.staffId() == null ? new StaffMember()
                : staffRepository.findById(in.staffId()).orElseThrow(() -> notFound("Staff member not found."));
        String name = trim(in.fullName(), 100);
        if (name == null || name.length() < 3) throw bad("Enter the full name.");
        String category = upper(in.category(), "SUPPORT");
        if (!CATEGORIES.contains(category)) throw bad("Choose teaching, support or administration.");
        String employer = upper(in.employer(), "SCHOOL");
        if (!EMPLOYERS.contains(employer)) throw bad("Employed by must be the school, an agency or other.");
        String terms = upperOrNull(in.employmentType());
        if (terms != null && !TERMS.contains(terms)) throw bad("Choose the terms: permanent, contract, casual, intern or volunteer.");
        String gender = upperOrNull(in.gender());
        if (gender != null && !GENDERS.contains(gender)) throw bad("Choose male or female.");
        if (in.dateOfBirth() != null && in.dateOfBirth().isAfter(LocalDate.now().minusYears(15))) throw bad("Check the date of birth.");
        if ("CONTRACT".equals(terms) && in.contractEnd() == null) throw bad("Enter when the contract ends.");
        if (in.contractEnd() != null && in.startDate() != null && in.contractEnd().isBefore(in.startDate())) throw bad("The contract can't end before it starts.");
        boolean active = in.active() == null || in.active();
        if (!active && in.dateLeft() == null) throw bad("Enter the date they left.");
        if (!active && trim(in.leftReason(), 200) == null) throw bad("Say why they left (e.g. transferred, resigned, contract ended).");

        String number = trim(in.staffNumber(), 20);
        if (number == null) number = s.getStaffNumber() != null ? s.getStaffNumber() : nextNumber();
        String idNo = trim(in.idNumber(), 20), tsc = upperNoSpaces(in.tscNumber());
        Long self = s.getStaffId();
        for (StaffMember o : staffRepository.findAll()) {
            if (Objects.equals(o.getStaffId(), self)) continue;
            if (number.equalsIgnoreCase(String.valueOf(o.getStaffNumber()))) throw bad("Staff number " + number + " is already used by " + o.getFullName() + ".");
            if (idNo != null && idNo.equalsIgnoreCase(String.valueOf(o.getIdNumber()))) throw bad("ID number " + idNo + " is already recorded for " + o.getFullName() + ".");
            if (tsc != null && tsc.equalsIgnoreCase(String.valueOf(o.getTscNumber()))) throw bad("TSC number " + tsc + " is already recorded for " + o.getFullName() + ".");
            if (in.teacherId() != null && in.teacherId().equals(o.getTeacherId())) throw bad("That teacher is already linked to " + o.getFullName() + ".");
        }
        List<StaffQualification> quals = new ArrayList<>();
        for (QualIn q : in.qualifications() == null ? List.<QualIn>of() : in.qualifications()) {
            String level = upper(q.level(), "OTHER");
            if (!LEVELS.contains(level)) throw bad("Unknown qualification level: " + q.level());
            String title = trim(q.title(), 100);
            if (title == null) throw bad("Every qualification needs a title (e.g. B.Ed Arts).");
            if (q.yearCompleted() != null && (q.yearCompleted() < 1950 || q.yearCompleted() > LocalDate.now().getYear())) throw bad("Check the year for " + title + ".");
            StaffQualification x = new StaffQualification();
            x.setLevel(level); x.setTitle(title); x.setInstitution(trim(q.institution(), 100)); x.setYearCompleted(q.yearCompleted());
            quals.add(x);
        }

        s.setStaffNumber(number.toUpperCase());
        s.setFullName(name.toUpperCase());
        s.setJobTitle(trim(in.jobTitle(), 60));
        s.setCategory(category);
        s.setTeacherId(in.teacherId());
        s.setPhone(trim(in.phone(), 20));
        s.setEmail(trim(in.email(), 100));
        s.setGender(gender);
        s.setDateOfBirth(in.dateOfBirth());
        s.setIdNumber(idNo);
        s.setResidence(trim(in.residence(), 100));
        s.setEmployer(employer);
        s.setTscNumber(tsc);
        s.setEmploymentType(terms);
        s.setStartDate(in.startDate());
        s.setContractEnd(in.contractEnd());
        s.setDepartment(trim(in.department(), 60));
        s.setActive(active);
        s.setDateLeft(active ? null : in.dateLeft());
        s.setLeftReason(active ? null : trim(in.leftReason(), 200));
        s.setOnPayroll(in.onPayroll() == null ? "SCHOOL".equals(employer) : in.onPayroll());   // the school pays its own staff
        s.setKraPin(in.kraPin() == null ? s.getKraPin() : upperNoSpaces(in.kraPin()));
        s.setNssfNumber(in.nssfNumber() == null ? s.getNssfNumber() : trim(in.nssfNumber(), 20));
        s.setShaNumber(in.shaNumber() == null ? s.getShaNumber() : trim(in.shaNumber(), 20));
        s.setNextOfKinName(trim(in.nextOfKinName(), 100));
        s.setNextOfKinRelationship(trim(in.nextOfKinRelationship(), 40));
        s.setNextOfKinPhone(trim(in.nextOfKinPhone(), 20));
        s.setNotes(trim(in.notes(), 200));
        if (s.getPayMethod() == null) s.setPayMethod("BANK");
        staffRepository.save(s);

        if (in.qualifications() != null) {
            qualificationRepository.deleteAll(qualificationRepository.findByStaffStaffIdOrderByYearCompletedDesc(s.getStaffId()));
            qualificationRepository.flush();
            for (StaffQualification x : quals) { x.setStaff(s); qualificationRepository.save(x); }
        }
        return Map.of("message", s.getFullName() + " saved (" + s.getStaffNumber() + ").", "staffId", s.getStaffId());
    }

    /** A staff record for every teacher who doesn't have one yet — employed and paid by the school. */
    @Transactional
    public Map<String, Object> importTeachers() {
        Set<Long> linked = new HashSet<>();
        staffRepository.findAll().forEach(s -> { if (s.getTeacherId() != null) linked.add(s.getTeacherId()); });
        int added = 0;
        for (Teacher t : teacherRepository.findAll()) {
            if (linked.contains(t.getTeacherId())) continue;
            StaffMember s = new StaffMember();
            s.setStaffNumber(nextNumber());
            s.setFullName((String.valueOf(t.getFirstName() == null ? "" : t.getFirstName()) + " " + (t.getLastName() == null ? "" : t.getLastName())).trim().toUpperCase());
            s.setTeacherId(t.getTeacherId());
            s.setCategory("TEACHING");
            s.setJobTitle("Teacher");
            s.setPhone(trim(t.getPhone(), 20));
            s.setEmail(trim(t.getEmail(), 100));
            s.setPayMethod("BANK");
            s.setEmployer("SCHOOL");
            s.setOnPayroll(true);                         // the school pays its teachers
            s.setActive(true);
            staffRepository.save(s);
            added++;
        }
        return Map.of("message", added == 0 ? "Every teacher already has a staff record." : added + " teacher(s) added to the staff register (on the school payroll). Fill in their details and pay on Payroll → Staff.");
    }

    /** STF-0001, STF-0002… (the next free number). */
    private String nextNumber() {
        int max = 0;
        for (StaffMember s : staffRepository.findAll()) {
            String n = String.valueOf(s.getStaffNumber());
            if (n.toUpperCase().matches("STF-\\d+")) max = Math.max(max, Integer.parseInt(n.substring(4)));
        }
        return String.format("STF-%04d", max + 1);
    }

    private static Map<String, Object> map(StaffMember s, List<StaffQualification> quals) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("staffId", s.getStaffId());
        m.put("staffNumber", s.getStaffNumber());
        m.put("fullName", s.getFullName());
        m.put("jobTitle", s.getJobTitle());
        m.put("category", s.getCategory());
        m.put("teacherId", s.getTeacherId());
        m.put("phone", s.getPhone());
        m.put("email", s.getEmail());
        m.put("gender", s.getGender());
        m.put("dateOfBirth", str(s.getDateOfBirth()));
        m.put("idNumber", s.getIdNumber());
        m.put("residence", s.getResidence());
        m.put("employer", s.getEmployer() == null ? "SCHOOL" : s.getEmployer());
        m.put("tscNumber", s.getTscNumber());
        m.put("employmentType", s.getEmploymentType());
        m.put("startDate", str(s.getStartDate()));
        m.put("contractEnd", str(s.getContractEnd()));
        m.put("department", s.getDepartment());
        m.put("active", s.isActive());
        m.put("dateLeft", str(s.getDateLeft()));
        m.put("leftReason", s.getLeftReason());
        m.put("onPayroll", s.isOnPayroll());
        m.put("kraPin", s.getKraPin());
        m.put("nssfNumber", s.getNssfNumber());
        m.put("shaNumber", s.getShaNumber());
        m.put("nextOfKinName", s.getNextOfKinName());
        m.put("nextOfKinRelationship", s.getNextOfKinRelationship());
        m.put("nextOfKinPhone", s.getNextOfKinPhone());
        m.put("notes", s.getNotes());
        List<Map<String, Object>> q = new ArrayList<>();
        for (StaffQualification x : quals) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("level", x.getLevel()); r.put("title", x.getTitle()); r.put("institution", x.getInstitution()); r.put("yearCompleted", x.getYearCompleted());
            q.add(r);
        }
        m.put("qualifications", q);
        m.put("contractDaysLeft", s.getContractEnd() == null || !s.isActive() ? null : ChronoUnit.DAYS.between(LocalDate.now(), s.getContractEnd()));
        m.put("age", s.getDateOfBirth() == null ? null : ChronoUnit.YEARS.between(s.getDateOfBirth(), LocalDate.now()));
        // What's still missing (for the "incomplete records" list)
        List<String> missing = new ArrayList<>();
        if (s.isActive()) {
            if (blank(s.getPhone())) missing.add("phone");
            if (blank(s.getIdNumber())) missing.add("ID number");
            if ("TEACHING".equals(s.getCategory()) && blank(s.getTscNumber())) missing.add("TSC registration number");
            if (s.isOnPayroll() && blank(s.getKraPin())) missing.add("KRA PIN");
            if (blank(s.getNextOfKinName()) || blank(s.getNextOfKinPhone())) missing.add("next of kin");
            if (s.getStartDate() == null) missing.add("start date");
        }
        m.put("missing", missing);
        return m;
    }

    private static boolean blank(String v) { return v == null || v.isBlank(); }
    private static String str(LocalDate d) { return d == null ? null : d.toString(); }
    private static String trim(String v, int max) { if (v == null || v.isBlank()) return null; String t = v.trim(); return t.length() > max ? t.substring(0, max) : t; }
    private static String upper(String v, String dflt) { return v == null || v.isBlank() ? dflt : v.trim().toUpperCase(); }
    private static String upperOrNull(String v) { return v == null || v.isBlank() ? null : v.trim().toUpperCase(); }
    private static String upperNoSpaces(String v) { return v == null || v.isBlank() ? null : v.replaceAll("\\s+", "").toUpperCase(); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
}
