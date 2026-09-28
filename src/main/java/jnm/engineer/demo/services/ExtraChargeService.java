package jnm.engineer.demo.services;

import jnm.engineer.demo.models.ChargeEntry;
import jnm.engineer.demo.models.OptionalCharge;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Student;
import jnm.engineer.demo.repositories.ChargeEntryRepository;
import jnm.engineer.demo.repositories.OptionalChargeRepository;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.repositories.MoneyGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Optional / one-off charges and their tick lists.
 *   PER_TERM (lunch, porridge, meals): tick learners per term; at most once per learner per term.
 *   ONCE (interview, admission, …):     at most once per learner, ever.
 * Unticking CANCELS the entry (kept on record, no longer counted in the balance).
 */
@Service
@RequiredArgsConstructor
public class ExtraChargeService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");

    private final OptionalChargeRepository chargeRepository;
    private final ChargeEntryRepository entryRepository;
    private final StudentService studentService;
    private final SettingsService settingsService;
    private final SchoolClassRepository schoolClassRepository;
    private final MoneyGroupRepository moneyGroupRepository;

    public record ChargeIn(Long chargeId, String name, BigDecimal amount, String kind, List<String> sections, Boolean active,
                           BigDecimal monthlyAmount, BigDecimal dailyAmount,
                           Map<String, BigDecimal> sectionAmounts, List<Long> classIds, Boolean teacherCanTick, Long groupId) {}
    /** One learner's price for a charge: basis TERM / MONTHS / DAYS / CUSTOM (see ChargePricing). */
    public record PriceIn(Long studentId, String yearLabel, Integer term, String basis, Integer quantity, BigDecimal amount, String note) {}
    public record ApplyIn(String yearLabel, Integer term, List<Long> add, List<Long> remove) {}

    // ══ The list of charges ═════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCharges() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (OptionalCharge c : chargeRepository.findAllByOrderByActiveDescNameAsc()) out.add(chargeMap(c));
        return out;
    }

    @Transactional
    public Map<String, Object> saveCharge(ChargeIn in) {
        String name = in.name() == null ? "" : in.name().trim();
        if (name.isEmpty()) throw bad("Give the charge a name, e.g. Lunch.");
        if (name.length() > 80) throw bad("The name is too long.");
        if (in.amount() == null || in.amount().signum() <= 0) throw bad("Amount must be more than 0.");
        if (in.amount().stripTrailingZeros().scale() > 2) throw bad("Amount can have at most 2 decimal places.");
        if (in.amount().compareTo(MAX_AMOUNT) > 0) throw bad("Amount looks too large — please check it.");
        OptionalCharge.Kind kind;
        try { kind = OptionalCharge.Kind.valueOf(String.valueOf(in.kind()).trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw bad("Choose whether it is charged each term or once."); }
        List<String> known = settingsService.sectionCodes();
        List<String> secs = in.sections() == null ? List.of() : in.sections().stream()
                .map(s -> String.valueOf(s).trim().toUpperCase())
                .filter(known::contains).distinct()
                .sorted(Comparator.comparingInt(known::indexOf)).toList();
        if (secs.isEmpty()) throw bad("Choose at least one section it applies to.");
        String sections = String.join(",", secs);

        boolean duplicate = in.chargeId() == null
                ? chargeRepository.existsByNameIgnoreCaseAndSections(name, sections)
                : chargeRepository.existsByNameIgnoreCaseAndSectionsAndChargeIdNot(name, sections, in.chargeId());
        if (duplicate) throw new ResponseStatusException(HttpStatus.CONFLICT, "\"" + name + "\" already exists for these sections.");

        OptionalCharge c = in.chargeId() == null ? new OptionalCharge()
                : chargeRepository.findById(in.chargeId()).orElseThrow(() -> notFound("Charge not found."));
        c.setName(name);
        c.setAmount(in.amount().setScale(2, RoundingMode.UNNECESSARY));
        c.setKind(kind);
        c.setSections(sections);
        c.setActive(in.active() == null || in.active());
        c.setTeacherCanTick(Boolean.TRUE.equals(in.teacherCanTick()));
        c.setGroup(in.groupId() == null ? null
                : moneyGroupRepository.findById(in.groupId()).orElseThrow(() -> bad("That money group no longer exists — refresh the page.")));

        // Different price per section (optional): only for the chosen sections
        Map<String, BigDecimal> perSection = new LinkedHashMap<>();
        if (in.sectionAmounts() != null) {
            for (String sec : secs) {
                BigDecimal v = in.sectionAmounts().get(sec);
                if (v == null) continue;
                if (v.signum() <= 0 || v.stripTrailingZeros().scale() > 2 || v.compareTo(MAX_AMOUNT) > 0)
                    throw bad("The " + settingsService.sectionName(sec) + " price must be more than 0, with at most 2 decimal places.");
                perSection.put(sec, v.setScale(2, RoundingMode.HALF_UP));
            }
        }
        c.setSectionAmounts(perSection.isEmpty() ? null : perSection.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue().toPlainString()).collect(java.util.stream.Collectors.joining(",")));

        // Only some classes (optional): must be real classes in the chosen sections
        List<Long> only = new ArrayList<>();
        for (Long id : in.classIds() == null ? List.<Long>of() : in.classIds()) {
            if (id == null || only.contains(id)) continue;
            SchoolClass cls = schoolClassRepository.findById(id).orElseThrow(() -> bad("A chosen class no longer exists — refresh and try again."));
            if (!secs.contains(cls.getSection())) throw bad(cls.getClassName() + " is not in the sections this charge applies to.");
            only.add(id);
        }
        String classIdText = only.isEmpty() ? null : only.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        if (classIdText != null && classIdText.length() > 500) throw bad("Too many classes chosen — leave it empty to mean every class.");
        c.setClassIds(classIdText);

        if (kind == OptionalCharge.Kind.PER_TERM) {
            c.setMonthlyAmount(ChargePricing.optionalRate(in.monthlyAmount(), "Monthly rate"));
            c.setDailyAmount(ChargePricing.optionalRate(in.dailyAmount(), "Daily rate"));
        } else {                       // once-only charges have no part-term rates
            c.setMonthlyAmount(null);
            c.setDailyAmount(null);
        }
        chargeRepository.save(c);
        Map<String, Object> out = new LinkedHashMap<>(chargeMap(c));
        out.put("message", "\"" + name + "\" saved. Learners already charged keep the amount they were charged.");
        return out;
    }

    // ══ Tick list for one charge ════════════════════════════════════════════
    /**
     * Every learner in the charge's sections (optionally one class), with whether they're ticked
     * for this term. For ONCE charges, a learner charged in another term shows as locked.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> learners(Long chargeId, String yearLabel, Integer term, Long classId) {
        OptionalCharge c = chargeRepository.findById(chargeId).orElseThrow(() -> notFound("Charge not found."));
        String year = requireYear(yearLabel);
        requireTerm(term);

        // Active entries: this term (PER_TERM) or any time (ONCE)
        Map<Long, ChargeEntry> entries = new HashMap<>();
        List<ChargeEntry> list = c.getKind() == OptionalCharge.Kind.ONCE
                ? entryRepository.findByChargeChargeIdAndCancelledFalse(chargeId)
                : entryRepository.findByChargeChargeIdAndYearLabelAndTermAndCancelledFalse(chargeId, year, term);
        for (ChargeEntry e : list) entries.put(e.getStudent().getStudentId(), e);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Student st : studentService.getAllStudents()) {
            SchoolClass cls = st.getSchoolClass();
            if (!c.appliesTo(cls)) continue;
            if (classId != null && !classId.equals(cls.getClassId())) continue;
            ChargeEntry e = entries.get(st.getStudentId());
            boolean thisTerm = e != null && year.equals(e.getYearLabel()) && term.equals(e.getTerm());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("studentId", st.getStudentId());
            r.put("admissionNumber", st.getAdmissionNumber());
            r.put("name", st.getFirstName() + " " + st.getLastName());
            r.put("classId", cls.getClassId());
            r.put("className", cls.getClassName());
            r.put("section", cls.getSection());
            r.put("standardAmount", c.amountFor(cls.getSection()));   // what ticking charges this learner
            r.put("ticked", e != null);
            r.put("locked", e != null && !thisTerm);   // ONCE charge made in another term — can't change here
            r.put("chargedIn", e != null ? e.getYearLabel() + " T" + e.getTerm() : null);
            r.put("amount", e != null ? e.getAmount() : null);
            r.put("basis", e != null ? ChargePricing.parseStored(e.getBasis()).name() : null);
            r.put("quantity", e != null ? e.getQuantity() : null);
            r.put("priceNote", e != null ? e.getPriceNote() : null);
            r.put("priceLabel", e != null ? ChargePricing.label(e.getBasis(), e.getQuantity()).trim() : null);
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("className")))
                .thenComparing(r -> String.valueOf(r.get("name"))));
        return Map.of("charge", chargeMap(c), "yearLabel", year, "term", term, "learners", rows);
    }

    /** Adds the charge to newly ticked learners and cancels it for unticked ones — all or nothing. */
    @Transactional
    public Map<String, Object> apply(Long chargeId, ApplyIn in, String who) {
        OptionalCharge c = chargeRepository.findById(chargeId).orElseThrow(() -> notFound("Charge not found."));
        String year = requireYear(in.yearLabel());
        Integer term = in.term();
        requireTerm(term);
        LocalDateTime now = LocalDateTime.now();

        List<ChargeEntry> active = c.getKind() == OptionalCharge.Kind.ONCE
                ? entryRepository.findByChargeChargeIdAndCancelledFalse(chargeId)
                : entryRepository.findByChargeChargeIdAndYearLabelAndTermAndCancelledFalse(chargeId, year, term);
        Map<Long, ChargeEntry> byStudent = new HashMap<>();
        for (ChargeEntry e : active) byStudent.put(e.getStudent().getStudentId(), e);

        int added = 0, removed = 0;
        List<String> problems = new ArrayList<>();

        for (Long sid : in.add() == null ? List.<Long>of() : in.add()) {
            if (byStudent.containsKey(sid)) continue;                         // already charged — nothing to do
            if (!c.isActive()) { problems.add("\"" + c.getName() + "\" is switched off"); break; }
            Student st;
            try { st = studentService.getById(sid); } catch (RuntimeException e) { problems.add("Learner " + sid + " not found"); continue; }
            SchoolClass cls = st.getSchoolClass();
            if (!c.appliesTo(cls)) {
                problems.add(st.getFirstName() + " " + st.getLastName() + " is not in a class this charge applies to");
                continue;
            }
            ChargeEntry e = new ChargeEntry();
            e.setStudent(st);
            e.setCharge(c);
            e.setSchoolClass(cls);
            e.setName(c.getName());
            e.setAmount(c.amountFor(cls.getSection()));
            e.setBasis(ChargePricing.Basis.TERM.name());
            e.setYearLabel(year);
            e.setTerm(term);
            e.setCreatedBy(who);
            e.setCreatedAt(now);
            entryRepository.save(e);
            byStudent.put(sid, e);
            added++;
        }
        for (Long sid : in.remove() == null ? List.<Long>of() : in.remove()) {
            ChargeEntry e = byStudent.get(sid);
            if (e == null) continue;
            if (!year.equals(e.getYearLabel()) || !term.equals(e.getTerm())) {
                problems.add("A once-only charge can only be removed in the term it was charged (" + e.getYearLabel() + " T" + e.getTerm() + ")");
                continue;
            }
            e.setCancelled(true);
            e.setCancelReason("Removed from the " + c.getName() + " list");
            e.setCancelledBy(who);
            e.setCancelledAt(now);
            entryRepository.save(e);
            removed++;
        }
        if (!problems.isEmpty()) {
            // Roll back everything so a list is never half-saved
            throw bad("Nothing was saved: " + String.join("; ", new LinkedHashSet<>(problems)));
        }
        return Map.of("added", added, "removed", removed,
                "message", c.getName() + ": " + added + " learner(s) charged, " + removed + " removed.");
    }

    // ══ One learner's price ═════════════════════════════════════════════════
    /**
     * Charges (or re-prices) one learner: full term, some months, some days, or an agreed amount.
     * A price change CANCELS the old entry and adds a new one, so the statement shows the history.
     */
    @Transactional
    public Map<String, Object> setPrice(Long chargeId, PriceIn in, String who) {
        OptionalCharge c = chargeRepository.findById(chargeId).orElseThrow(() -> notFound("Charge not found."));
        String year = requireYear(in.yearLabel());
        Integer term = in.term();
        requireTerm(term);
        if (in.studentId() == null) throw bad("Choose the learner.");
        Student st;
        try { st = studentService.getById(in.studentId()); } catch (RuntimeException e) { throw notFound("Learner not found."); }
        SchoolClass cls = st.getSchoolClass();
        if (!c.appliesTo(cls))
            throw bad(st.getFirstName() + " " + st.getLastName() + " is not in a class " + c.getName() + " applies to.");

        boolean once = c.getKind() == OptionalCharge.Kind.ONCE;
        ChargePricing.Basis wanted = ChargePricing.parseStored(in.basis());
        if (once && (wanted == ChargePricing.Basis.MONTHS || wanted == ChargePricing.Basis.DAYS))
            throw bad(c.getName() + " is charged once — use the normal price or an agreed amount.");
        ChargePricing.Price price = ChargePricing.resolve(in.basis(), in.quantity(), in.amount(), in.note(),
                c.amountFor(cls.getSection()), c.getMonthlyAmount(), c.getDailyAmount(), c.getName());

        List<ChargeEntry> active = once
                ? entryRepository.findByChargeChargeIdAndCancelledFalse(chargeId)
                : entryRepository.findByChargeChargeIdAndYearLabelAndTermAndCancelledFalse(chargeId, year, term);
        ChargeEntry old = active.stream().filter(e -> e.getStudent().getStudentId().equals(st.getStudentId())).findFirst().orElse(null);
        if (old != null && (!year.equals(old.getYearLabel()) || !term.equals(old.getTerm())))
            throw bad(c.getName() + " was charged in " + old.getYearLabel() + " Term " + old.getTerm() + " — change it in that term.");
        if (old != null && price.sameAs(old.getBasis(), old.getQuantity(), old.getAmount())
                && Objects.equals(old.getPriceNote(), price.note())) {
            return Map.of("message", "No change — " + c.getName() + " is already charged that way.");
        }
        if (old == null && !c.isActive()) throw bad("\"" + c.getName() + "\" is switched off.");

        LocalDateTime now = LocalDateTime.now();
        if (old != null) {
            old.setCancelled(true);
            old.setCancelReason("Price changed to " + price.amount().toPlainString() + price.label());
            old.setCancelledBy(who);
            old.setCancelledAt(now);
            entryRepository.save(old);
        }
        ChargeEntry e = new ChargeEntry();
        e.setStudent(st);
        e.setCharge(c);
        e.setSchoolClass(cls);
        e.setName(c.getName());
        e.setAmount(price.amount());
        e.setBasis(price.basis().name());
        e.setQuantity(price.quantity());
        e.setRate(price.rate());
        e.setPriceNote(price.note());
        e.setYearLabel(year);
        e.setTerm(term);
        e.setCreatedBy(who);
        e.setCreatedAt(now);
        entryRepository.save(e);
        return Map.of("message", st.getFirstName() + " " + st.getLastName() + ": " + c.getName() + price.label()
                + " — " + price.amount().toPlainString() + (old != null ? " (was " + old.getAmount().toPlainString() + ")" : "") + ".");
    }

    // ══ helpers ═════════════════════════════════════════════════════════════
    private static Map<String, Object> chargeMap(OptionalCharge c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chargeId", c.getChargeId());
        m.put("name", c.getName());
        m.put("amount", c.getAmount());
        m.put("kind", c.getKind().name());
        m.put("sections", c.sectionList());
        m.put("active", c.isActive());
        m.put("monthlyAmount", c.getMonthlyAmount());
        m.put("dailyAmount", c.getDailyAmount());
        m.put("sectionAmounts", c.sectionAmountMap());
        m.put("classIds", new ArrayList<>(c.classIdSet()));
        m.put("teacherCanTick", c.teachersMayTick());
        m.put("groupId", c.getGroup() != null ? c.getGroup().getGroupId() : null);
        m.put("groupName", c.getGroup() != null ? c.getGroup().getName() : null);
        return m;
    }

    private static String requireYear(String y) {
        String year = y == null ? "" : y.trim();
        if (!year.matches("\\d{4}")) throw bad("Year must be 4 digits, e.g. 2026.");
        return year;
    }

    private static void requireTerm(Integer t) {
        if (t == null || t < 1 || t > 3) throw bad("Term must be 1, 2 or 3.");
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
}
