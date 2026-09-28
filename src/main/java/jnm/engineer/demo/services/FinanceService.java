package jnm.engineer.demo.services;

import jnm.engineer.demo.models.*;
import jnm.engineer.demo.models.ChargeEntry;
import jnm.engineer.demo.models.FeeItem;
import jnm.engineer.demo.models.FeeStructure;
import jnm.engineer.demo.models.Invoice;
import jnm.engineer.demo.models.Payment;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.TransportSubscription;
import jnm.engineer.demo.repositories.ChargeEntryRepository;
import jnm.engineer.demo.repositories.FeeStructureRepository;
import jnm.engineer.demo.repositories.InvoiceRepository;
import jnm.engineer.demo.repositories.PaymentRepository;
import jnm.engineer.demo.repositories.TransportSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * All fee rules live here: fee structures, billing, payments, reversals, balances
 * and statements. Other parts of the system (dashboard, student profile, a future
 * parent portal or SMS reminders) can call this service directly — e.g. balanceOf().
 *
 * Problems are reported by throwing ResponseStatusException with a clear message;
 * Spring turns that into the HTTP status + {"message": ...} the pages already show.
 *
 * Balance = term fees + extra charges + transport − payments (reversed payments and
 * cancelled charges don't count),
 * so arrears and overpayments carry forward on their own.
 */
@Service
@RequiredArgsConstructor
public class FinanceService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");   // sanity limit per line / payment

    private final FeeStructureRepository structureRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ChargeEntryRepository chargeEntryRepository;
    private final TransportSubscriptionRepository transportRepository;
    private final StudentService studentService;
    private final SettingsService settingsService;

    // ── Inputs (used directly as request bodies by FinanceController) ──
    public record ItemIn(String name, BigDecimal amount) {}
    public record StructureIn(String section, String yearLabel, Integer term, List<ItemIn> items) {}
    public record PaymentIn(Long studentId, BigDecimal amount, String method, String reference, String payerName, LocalDate paidOn) {}
    public record ReverseIn(String reason) {}

    // ══ Fee structures ══════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStructures(String yearLabel, Integer term) {
        List<FeeStructure> list = (yearLabel != null && term != null)
                ? structureRepository.findByYearLabelAndTerm(yearLabel.trim(), term)
                : structureRepository.findAll();
        List<Map<String, Object>> out = new ArrayList<>();
        list.stream()
                .sorted(Comparator.comparing(FeeStructure::getYearLabel).reversed()
                        .thenComparing(FeeStructure::getTerm, Comparator.reverseOrder())
                        .thenComparing(FeeStructure::getSection))
                .forEach(s -> out.add(structureMap(s)));
        return out;
    }

    @Transactional
    public Map<String, Object> saveStructure(StructureIn in) {
        String section = in.section() == null ? "" : in.section().trim().toUpperCase();
        String year = in.yearLabel() == null ? "" : in.yearLabel().trim();
        if (!settingsService.sectionCodes().contains(section)) throw bad("Choose a valid section.");
        if (!year.matches("\\d{4}")) throw bad("Year must be 4 digits, e.g. 2026.");
        if (in.term() == null || in.term() < 1 || in.term() > 3) throw bad("Term must be 1, 2 or 3.");
        if (in.items() == null || in.items().isEmpty()) throw bad("Add at least one fee item.");

        List<FeeItem> items = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ItemIn i : in.items()) {
            String name = i.name() == null ? "" : i.name().trim();
            if (name.isEmpty()) throw bad("Every fee item needs a name.");
            if (!names.add(name.toLowerCase())) throw bad("\"" + name + "\" appears twice.");
            String problem = checkMoney(i.amount(), true);
            if (problem != null) throw bad(name + ": " + problem);
            items.add(new FeeItem(name, i.amount().setScale(2, RoundingMode.UNNECESSARY)));
        }

        FeeStructure s = structureRepository.findBySectionAndYearLabelAndTerm(section, year, in.term()).orElseGet(FeeStructure::new);
        boolean existed = s.getStructureId() != null;
        s.setSection(section);
        s.setYearLabel(year);
        s.setTerm(in.term());
        s.getItems().clear();
        s.getItems().addAll(items);
        structureRepository.save(s);

        long billed = invoiceRepository.findByYearLabelAndTerm(year, in.term()).stream()
                .filter(inv -> inv.getSchoolClass() != null && section.equals(inv.getSchoolClass().getSection())).count();
        Map<String, Object> out = new LinkedHashMap<>(structureMap(s));
        out.put("message", (existed ? "Fees updated." : "Fees saved.")
                + (billed > 0 ? " Note: " + billed + " learner(s) were already billed this term and keep their old amounts." : ""));
        return out;
    }

    @Transactional
    public void deleteStructure(Long id) {
        if (!structureRepository.existsById(id)) throw notFound("Fee structure not found.");
        structureRepository.deleteById(id);   // invoices keep their own copies of the lines
    }

    // ══ Billing ═════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Map<String, Object> previewBilling(String yearLabel, Integer term) {
        return planBilling(yearLabel.trim(), term, null, false);
    }

    /** Bills every learner who has a class, fees set for their section, and no invoice yet. All or nothing. */
    @Transactional
    public Map<String, Object> runBilling(String yearLabel, Integer term, String who) {
        return planBilling(yearLabel.trim(), term, who, true);
    }

    private Map<String, Object> planBilling(String year, Integer term, String who, boolean create) {
        Map<String, FeeStructure> bySection = new HashMap<>();
        for (FeeStructure s : structureRepository.findByYearLabelAndTerm(year, term)) bySection.put(s.getSection(), s);

        List<String> sectionCodes = settingsService.sectionCodes();
        Map<String, int[]> perSection = new LinkedHashMap<>();   // [learners, alreadyBilled, toBill]
        sectionCodes.forEach(sec -> perSection.put(sec, new int[3]));
        int noClass = 0, created = 0;
        BigDecimal createdTotal = BigDecimal.ZERO;
        LocalDateTime now = LocalDateTime.now();

        for (Student st : studentService.getAllStudents()) {
            SchoolClass cls = st.getSchoolClass();
            if (cls == null || !sectionCodes.contains(String.valueOf(cls.getSection()))) { noClass++; continue; }
            int[] c = perSection.get(cls.getSection());
            c[0]++;
            if (invoiceRepository.existsByStudentStudentIdAndYearLabelAndTerm(st.getStudentId(), year, term)) { c[1]++; continue; }
            FeeStructure fs = bySection.get(cls.getSection());
            if (fs == null) continue;                       // no fees set for this section yet
            c[2]++;
            if (create) {
                Invoice inv = new Invoice();
                inv.setStudent(st);
                inv.setSchoolClass(cls);
                inv.setYearLabel(year);
                inv.setTerm(term);
                List<FeeItem> copy = new ArrayList<>();
                fs.getItems().forEach(i -> copy.add(new FeeItem(i.getName(), i.getAmount())));
                inv.setLines(copy);
                inv.setAmount(fs.getTotal());
                inv.setCreatedBy(who);
                inv.setCreatedAt(now);
                invoiceRepository.save(inv);
                created++;
                createdTotal = createdTotal.add(fs.getTotal());
            }
        }

        List<Map<String, Object>> sections = new ArrayList<>();
        perSection.forEach((sec, c) -> {
            FeeStructure fs = bySection.get(sec);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("section", sec);
            m.put("hasFees", fs != null);
            m.put("feeTotal", fs != null ? fs.getTotal() : null);
            m.put("learners", c[0]);
            m.put("alreadyBilled", c[1]);
            m.put("toBill", c[2]);
            sections.add(m);
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("yearLabel", year);
        out.put("term", term);
        out.put("sections", sections);
        out.put("learnersWithoutClass", noClass);
        if (create) {
            out.put("created", created);
            out.put("createdTotal", createdTotal);
            out.put("message", created == 0 ? "Nobody new to bill." : "Billed " + created + " learner(s).");
        }
        return out;
    }

    // ══ Balances ════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Map<String, Object> balances(Long classId) {
        Map<Long, BigDecimal> billed = totals(invoiceRepository.totalsByStudent());
        totals(chargeEntryRepository.totalsByStudent()).forEach((sid, amt) -> billed.merge(sid, amt, BigDecimal::add));
        totals(transportRepository.totalsByStudent()).forEach((sid, amt) -> billed.merge(sid, amt, BigDecimal::add));
        Map<Long, BigDecimal> paid = totals(paymentRepository.totalsByStudent());
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal sumBilled = BigDecimal.ZERO, sumPaid = BigDecimal.ZERO, sumOwed = BigDecimal.ZERO;
        for (Student st : studentService.getAllStudents()) {
            SchoolClass cls = st.getSchoolClass();
            if (classId != null && (cls == null || !classId.equals(cls.getClassId()))) continue;
            BigDecimal b = billed.getOrDefault(st.getStudentId(), BigDecimal.ZERO);
            BigDecimal p = paid.getOrDefault(st.getStudentId(), BigDecimal.ZERO);
            BigDecimal bal = b.subtract(p);
            Map<String, Object> r = studentMap(st);
            r.put("billed", b);
            r.put("paid", p);
            r.put("balance", bal);
            rows.add(r);
            sumBilled = sumBilled.add(b);
            sumPaid = sumPaid.add(p);
            if (bal.signum() > 0) sumOwed = sumOwed.add(bal);
        }
        rows.sort(Comparator.comparing(r -> String.valueOf(r.get("name"))));
        return Map.of("students", rows, "totalBilled", sumBilled, "totalPaid", sumPaid, "totalOwed", sumOwed);
    }

    /** One learner's balance (positive = owes, negative = credit). Reusable anywhere. */
    @Transactional(readOnly = true)
    public BigDecimal balanceOf(Long studentId) {
        BigDecimal b = invoiceRepository.findByStudentStudentIdOrderByCreatedAtAsc(studentId).stream()
                .map(Invoice::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extras = chargeEntryRepository.findByStudentStudentIdOrderByCreatedAtAsc(studentId).stream()
                .filter(x -> !x.isCancelled()).map(ChargeEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal p = paymentRepository.findByStudentStudentIdOrderByPaidOnAscPaymentIdAsc(studentId).stream()
                .filter(x -> !x.isReversed()).map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal transport = transportRepository.findByStudentStudentIdOrderByCreatedAtAsc(studentId).stream()
                .filter(x -> !x.isCancelled()).map(TransportSubscription::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return b.add(extras).add(transport).subtract(p);
    }

    // ══ Statement ═══════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Map<String, Object> statement(Long studentId) {
        Student st = findStudent(studentId);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Invoice inv : invoiceRepository.findByStudentStudentIdOrderByCreatedAtAsc(studentId)) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("type", "INVOICE");
            e.put("date", inv.getCreatedAt().toLocalDate().toString());
            e.put("description", "Fees — " + inv.getYearLabel() + " Term " + inv.getTerm());
            e.put("debit", inv.getAmount());
            e.put("credit", null);
            List<Map<String, Object>> lines = new ArrayList<>();
            inv.getLines().forEach(l -> lines.add(Map.of("name", l.getName(), "amount", l.getAmount())));
            e.put("lines", lines);
            e.put("sortKey", inv.getCreatedAt().toLocalDate() + "|0|" + inv.getInvoiceId());
            entries.add(e);
        }
        for (ChargeEntry c : chargeEntryRepository.findByStudentStudentIdOrderByCreatedAtAsc(studentId)) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("type", "CHARGE");
            e.put("date", c.getCreatedAt().toLocalDate().toString());
            e.put("description", c.getName() + ChargePricing.label(c.getBasis(), c.getQuantity()) + " — " + c.getYearLabel() + " Term " + c.getTerm() + (c.isCancelled() ? " (CANCELLED)" : ""));
            e.put("priceNote", c.getPriceNote());
            e.put("debit", c.isCancelled() ? null : c.getAmount());
            e.put("credit", null);
            e.put("cancelled", c.isCancelled());
            e.put("cancelReason", c.getCancelReason());
            e.put("sortKey", c.getCreatedAt().toLocalDate() + "|0|c" + c.getEntryId());
            entries.add(e);
        }
        for (TransportSubscription t : transportRepository.findByStudentStudentIdOrderByCreatedAtAsc(studentId)) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("type", "TRANSPORT");
            e.put("date", t.getCreatedAt().toLocalDate().toString());
            e.put("description", t.getDescription() + ChargePricing.label(t.getBasis(), t.getQuantity()) + " — " + t.getYearLabel() + " Term " + t.getTerm() + (t.isCancelled() ? " (CANCELLED)" : ""));
            e.put("priceNote", t.getPriceNote());
            e.put("debit", t.isCancelled() ? null : t.getAmount());
            e.put("credit", null);
            e.put("cancelled", t.isCancelled());
            e.put("cancelReason", t.getCancelReason());
            e.put("sortKey", t.getCreatedAt().toLocalDate() + "|0|t" + t.getSubscriptionId());
            entries.add(e);
        }
        for (Payment p : paymentRepository.findByStudentStudentIdOrderByPaidOnAscPaymentIdAsc(studentId)) {
            Map<String, Object> e = new LinkedHashMap<>(paymentMap(p));
            e.put("type", "PAYMENT");
            e.put("date", p.getPaidOn().toString());
            e.put("description", methodLabel(p.getMethod()) + " " + p.getReference() + (p.isReversed() ? " (REVERSED)" : ""));
            e.put("debit", null);
            e.put("credit", p.isReversed() ? null : p.getAmount());
            e.put("sortKey", p.getPaidOn() + "|1|" + p.getPaymentId());
            entries.add(e);
        }
        entries.sort(Comparator.comparing(e -> String.valueOf(e.get("sortKey"))));
        BigDecimal running = BigDecimal.ZERO;
        for (Map<String, Object> e : entries) {
            if (e.get("debit") != null) running = running.add((BigDecimal) e.get("debit"));
            if (e.get("credit") != null) running = running.subtract((BigDecimal) e.get("credit"));
            e.put("balance", running);
            e.remove("sortKey");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("student", studentMap(st));
        out.put("entries", entries);
        out.put("balance", running);
        return out;
    }

    // ══ Payments ════════════════════════════════════════════════════════════
    /** Saves the payment AND its receipt number together — both or neither. */
    @Transactional
    public Map<String, Object> recordPayment(PaymentIn in, String who) {
        if (in.studentId() == null) throw bad("Choose the learner.");
        Student st = findStudent(in.studentId());

        String problem = checkMoney(in.amount(), false);
        if (problem != null) throw bad(problem);

        Payment.Method method;
        try { method = Payment.Method.valueOf(String.valueOf(in.method()).trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw bad("Payment method must be M-Pesa, Bank or Cheque."); }

        String ref = in.reference() == null ? "" : in.reference().trim().toUpperCase().replaceAll("\\s+", "");
        if (ref.length() < 4 || ref.length() > 40 || !ref.matches("[A-Z0-9/\\-]+")) {
            throw bad("Enter the " + referenceLabel(method) + " (letters and numbers only).");
        }
        // The same M-Pesa code / slip / cheque can't be entered twice
        if (paymentRepository.existsByMethodAndReferenceIgnoreCaseAndReversedFalse(method, ref)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This " + referenceLabel(method) + " (" + ref + ") has already been recorded.");
        }
        LocalDate paidOn = in.paidOn() != null ? in.paidOn() : LocalDate.now();
        if (paidOn.isAfter(LocalDate.now())) throw bad("The payment date can't be in the future.");

        Payment p = new Payment();
        p.setStudent(st);
        p.setAmount(in.amount().setScale(2, RoundingMode.UNNECESSARY));
        p.setMethod(method);
        p.setReference(ref);
        p.setPayerName(in.payerName() == null || in.payerName().isBlank() ? null : in.payerName().trim());
        p.setPaidOn(paidOn);
        p.setRecordedBy(who);
        p.setRecordedAt(LocalDateTime.now());
        paymentRepository.save(p);
        p.setReceiptNumber(String.format("RCT-%d-%05d", paidOn.getYear(), p.getPaymentId()));
        paymentRepository.save(p);

        Map<String, Object> out = new LinkedHashMap<>(paymentMap(p));
        out.put("student", studentMap(st));
        out.put("newBalance", balanceOf(st.getStudentId()));
        out.put("message", "Payment recorded. Receipt " + p.getReceiptNumber() + ".");
        return out;
    }

    /** Marks a payment reversed (never deletes it). */
    @Transactional
    public Map<String, Object> reversePayment(Long paymentId, String reason, String who) {
        Payment p = paymentRepository.findById(paymentId).orElseThrow(() -> notFound("Payment not found."));
        if (p.isReversed()) throw bad("This payment was already reversed.");
        String r = reason == null ? "" : reason.trim();
        if (r.length() < 3) throw bad("Give a reason, e.g. \"cheque bounced\".");
        p.setReversed(true);
        p.setReversalReason(r.length() > 200 ? r.substring(0, 200) : r);
        p.setReversedBy(who);
        p.setReversedAt(LocalDateTime.now());
        paymentRepository.save(p);
        return Map.of("message", "Receipt " + p.getReceiptNumber() + " reversed.",
                "newBalance", balanceOf(p.getStudent().getStudentId()));
    }

    /** Day book: payments in a date range with totals per method (reversed ones excluded from totals). */
    @Transactional(readOnly = true)
    public Map<String, Object> payments(LocalDate from, LocalDate to) { return payments(from, to, null); }

    /** Same, optionally only learners now in one class; then each row also carries the learner's balance now. */
    @Transactional(readOnly = true)
    public Map<String, Object> payments(LocalDate from, LocalDate to, Long classId) {
        if (from.isAfter(to)) throw bad("The start date must be before the end date.");
        Map<Long, BigDecimal> balanceNow = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        for (Payment.Method m : Payment.Method.values()) byMethod.put(m.name(), BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;
        for (Payment p : paymentRepository.findByPaidOnBetweenOrderByPaidOnDescPaymentIdDesc(from, to)) {
            SchoolClass pc = p.getStudent().getSchoolClass();
            if (classId != null && (pc == null || !classId.equals(pc.getClassId()))) continue;
            Map<String, Object> r = new LinkedHashMap<>(paymentMap(p));
            r.put("student", studentMap(p.getStudent()));
            if (classId != null) {
                Long sid = p.getStudent().getStudentId();
                r.put("balanceNow", balanceNow.computeIfAbsent(sid, this::balanceOf));
            }
            rows.add(r);
            if (!p.isReversed()) {
                total = total.add(p.getAmount());
                byMethod.merge(p.getMethod().name(), p.getAmount(), BigDecimal::add);
            }
        }
        return Map.of("payments", rows, "total", total, "byMethod", byMethod);
    }

    // ══ Class sheet (balance slips / class balance list) ════════════════════
    /**
     * Each learner's position for one term, for printing. Payments clear the OLDEST charges first:
     *   brought forward = earlier terms' charges not yet covered by payments
     *   this term       = this term's charges, item by item
     *   paid this term  = what's left of payments after clearing earlier terms (up to this term's total)
     *   balance         = everything charged − everything paid (negative = credit)
     * Pick learners by class, by section, or all.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> classSheet(Long classId, String section, String yearLabel, Integer term) {
        String year = yearLabel == null ? "" : yearLabel.trim();
        if (!year.matches("\\d{4}")) throw bad("Year must be 4 digits, e.g. 2026.");
        if (term == null || term < 1 || term > 3) throw bad("Term must be 1, 2 or 3.");
        int thisKey = Integer.parseInt(year) * 10 + term;

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal sumThis = BigDecimal.ZERO, sumPaidThis = BigDecimal.ZERO, sumBf = BigDecimal.ZERO, sumOwed = BigDecimal.ZERO;
        int owing = 0, cleared = 0, credit = 0;
        for (Student st : studentService.getAllStudents()) {
            SchoolClass cls = st.getSchoolClass();
            if (cls == null) continue;
            if (classId != null && !classId.equals(cls.getClassId())) continue;
            if (classId == null && section != null && !section.isBlank() && !section.equals(cls.getSection())) continue;
            Long sid = st.getStudentId();

            BigDecimal earlier = BigDecimal.ZERO, current = BigDecimal.ZERO, later = BigDecimal.ZERO;
            List<Map<String, Object>> items = new ArrayList<>();
            for (Invoice inv : invoiceRepository.findByStudentStudentIdOrderByCreatedAtAsc(sid)) {
                int k = termKey(inv.getYearLabel(), inv.getTerm());
                if (k < thisKey) earlier = earlier.add(inv.getAmount());
                else if (k > thisKey) later = later.add(inv.getAmount());
                else {
                    current = current.add(inv.getAmount());
                    inv.getLines().forEach(l -> items.add(item(l.getName(), l.getAmount(), null)));
                }
            }
            for (ChargeEntry c : chargeEntryRepository.findByStudentStudentIdOrderByCreatedAtAsc(sid)) {
                if (c.isCancelled()) continue;
                int k = termKey(c.getYearLabel(), c.getTerm());
                if (k < thisKey) earlier = earlier.add(c.getAmount());
                else if (k > thisKey) later = later.add(c.getAmount());
                else {
                    current = current.add(c.getAmount());
                    items.add(item(c.getName() + ChargePricing.label(c.getBasis(), c.getQuantity()), c.getAmount(), c.getPriceNote()));
                }
            }
            for (TransportSubscription t : transportRepository.findByStudentStudentIdOrderByCreatedAtAsc(sid)) {
                if (t.isCancelled()) continue;
                int k = termKey(t.getYearLabel(), t.getTerm());
                if (k < thisKey) earlier = earlier.add(t.getAmount());
                else if (k > thisKey) later = later.add(t.getAmount());
                else {
                    current = current.add(t.getAmount());
                    items.add(item(t.getDescription() + ChargePricing.label(t.getBasis(), t.getQuantity()), t.getAmount(), t.getPriceNote()));
                }
            }
            BigDecimal paid = BigDecimal.ZERO;
            Payment last = null;
            for (Payment p : paymentRepository.findByStudentStudentIdOrderByPaidOnAscPaymentIdAsc(sid)) {
                if (p.isReversed()) continue;
                paid = paid.add(p.getAmount());
                last = p;
            }
            BigDecimal toEarlier = paid.min(earlier.max(BigDecimal.ZERO));
            BigDecimal bf = earlier.subtract(toEarlier);                       // unpaid from earlier terms
            BigDecimal left = paid.subtract(toEarlier);
            BigDecimal paidThis = left.min(current.max(BigDecimal.ZERO));
            BigDecimal balance = earlier.add(current).add(later).subtract(paid);

            Map<String, Object> r = studentMap(st);
            r.put("items", items);
            r.put("thisTerm", current);
            r.put("broughtForward", bf);
            r.put("paidThisTerm", paidThis);
            r.put("laterTerms", later);
            r.put("totalPaid", paid);
            r.put("balance", balance);
            r.put("lastPaidOn", last != null ? last.getPaidOn().toString() : null);
            r.put("lastPaidAmount", last != null ? last.getAmount() : null);
            rows.add(r);

            sumThis = sumThis.add(current);
            sumPaidThis = sumPaidThis.add(paidThis);
            sumBf = sumBf.add(bf);
            if (balance.signum() > 0) { owing++; sumOwed = sumOwed.add(balance); }
            else if (balance.signum() == 0) cleared++;
            else credit++;
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("className")))
                .thenComparing(r -> String.valueOf(r.get("name"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("yearLabel", year);
        out.put("term", term);
        out.put("learners", rows);
        out.put("totalThisTerm", sumThis);
        out.put("totalPaidThisTerm", sumPaidThis);
        out.put("totalBroughtForward", sumBf);
        out.put("totalOwed", sumOwed);
        out.put("owing", owing);
        out.put("cleared", cleared);
        out.put("inCredit", credit);
        return out;
    }

    private static int termKey(String yearLabel, Integer term) {
        try { return Integer.parseInt(String.valueOf(yearLabel).trim()) * 10 + (term == null ? 0 : term); }
        catch (NumberFormatException e) { return 0; }   // unreadable year: treat as earliest
    }

    private static Map<String, Object> item(String name, BigDecimal amount, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("amount", amount);
        if (note != null) m.put("note", note);
        return m;
    }

    // ══ helpers ═════════════════════════════════════════════════════════════
    private Student findStudent(Long id) {
        try { return studentService.getById(id); }
        catch (RuntimeException e) { throw notFound("Learner not found."); }
    }

    private static Map<Long, BigDecimal> totals(List<Object[]> rows) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) m.put((Long) r[0], (BigDecimal) r[1]);
        return m;
    }

    /** null if fine, otherwise the problem. */
    private static String checkMoney(BigDecimal amount, boolean allowZero) {
        if (amount == null) return "Enter an amount.";
        if (amount.signum() < 0 || (!allowZero && amount.signum() == 0)) return "Amount must be more than 0.";
        if (amount.stripTrailingZeros().scale() > 2) return "Amount can have at most 2 decimal places.";
        if (amount.compareTo(MAX_AMOUNT) > 0) return "Amount looks too large — please check it.";
        return null;
    }

    private static String methodLabel(Payment.Method m) {
        return switch (m) { case MPESA -> "M-Pesa"; case BANK -> "Bank"; case CHEQUE -> "Cheque"; };
    }

    private static String referenceLabel(Payment.Method m) {
        return switch (m) { case MPESA -> "M-Pesa code"; case BANK -> "bank slip number"; case CHEQUE -> "cheque number"; };
    }

    private static Map<String, Object> structureMap(FeeStructure s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("structureId", s.getStructureId());
        m.put("section", s.getSection());
        m.put("yearLabel", s.getYearLabel());
        m.put("term", s.getTerm());
        List<Map<String, Object>> items = new ArrayList<>();
        s.getItems().forEach(i -> items.add(Map.of("name", i.getName(), "amount", i.getAmount())));
        m.put("items", items);
        m.put("total", s.getTotal());
        return m;
    }

    private static Map<String, Object> paymentMap(Payment p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("paymentId", p.getPaymentId());
        m.put("receiptNumber", p.getReceiptNumber());
        m.put("amount", p.getAmount());
        m.put("method", p.getMethod().name());
        m.put("reference", p.getReference());
        m.put("payerName", p.getPayerName());
        m.put("paidOn", p.getPaidOn().toString());
        m.put("recordedBy", p.getRecordedBy());
        m.put("recordedAt", p.getRecordedAt() != null ? p.getRecordedAt().toString() : null);
        m.put("reversed", p.isReversed());
        m.put("reversalReason", p.getReversalReason());
        m.put("reversedBy", p.getReversedBy());
        return m;
    }

    private static Map<String, Object> studentMap(Student st) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentId", st.getStudentId());
        m.put("admissionNumber", st.getAdmissionNumber());
        m.put("name", st.getFirstName() + " " + st.getLastName());
        SchoolClass c = st.getSchoolClass();
        m.put("classId", c != null ? c.getClassId() : null);
        m.put("className", c != null ? c.getClassName() : null);
        m.put("stream", c != null ? c.getStream() : null);
        m.put("section", c != null ? c.getSection() : null);
        return m;
    }

    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
