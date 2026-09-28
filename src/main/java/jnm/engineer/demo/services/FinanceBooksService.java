package jnm.engineer.demo.services;

import jnm.engineer.demo.models.*;
import jnm.engineer.demo.models.ChargeEntry;
import jnm.engineer.demo.models.Expense;
import jnm.engineer.demo.models.FeeItem;
import jnm.engineer.demo.models.Invoice;
import jnm.engineer.demo.models.MoneyGroup;
import jnm.engineer.demo.models.TransportSubscription;
import jnm.engineer.demo.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * The school's books: daily expenses, and money in vs money out per money group.
 *
 *  Charged     — what learners were charged for the term, in the group
 *  Collected   — how much of that has been paid (payments split by PaymentAllocator:
 *                oldest term first, then the groups' priority order)
 *  Still owed  — charged − collected
 *  Expenses    — what was spent from the group in the term (voided ones left out)
 *  Profit      — collected − expenses   (and "if all is paid": charged − expenses)
 */
@Service
@RequiredArgsConstructor
public class FinanceBooksService {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000");
    private static final Set<String> METHODS = Set.of("CASH", "MPESA", "BANK", "CHEQUE");

    private final ExpenseRepository expenseRepository;
    private final MoneyGroupRepository groupRepository;
    private final MoneyGroupService moneyGroupService;
    private final InvoiceRepository invoiceRepository;
    private final ChargeEntryRepository chargeEntryRepository;
    private final TransportSubscriptionRepository transportRepository;
    private final PaymentRepository paymentRepository;

    public record ExpenseIn(LocalDate spentOn, String yearLabel, Integer term, Long groupId, String description,
                            String payee, BigDecimal amount, String method, String reference) {}

    // ══ Expenses ════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Map<String, Object> expenses(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw bad("Choose the dates.");
        if (from.isAfter(to)) throw bad("The start date must be before the end date.");
        return expenseList(expenseRepository.findBySpentOnBetweenOrderBySpentOnDescExpenseIdDesc(from, to));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> expensesForTerm(String yearLabel, Integer term) {
        return expenseList(expenseRepository.findByYearLabelAndTermOrderBySpentOnDescExpenseIdDesc(year(yearLabel), term(term)));
    }

    @Transactional
    public Map<String, Object> recordExpense(ExpenseIn in, String who) {
        if (in.spentOn() == null) throw bad("Enter the date.");
        if (in.spentOn().isAfter(LocalDate.now().plusDays(1))) throw bad("The date can't be in the future.");
        MoneyGroup g = in.groupId() == null ? null : groupRepository.findById(in.groupId()).orElse(null);
        if (g == null) throw bad("Choose the money group (e.g. Meals).");
        if (!g.isActive()) throw bad("\"" + g.getName() + "\" is switched off.");
        String desc = trim(in.description(), 200);
        if (desc == null || desc.length() < 3) throw bad("Say what the money was spent on.");
        if (in.amount() == null || in.amount().signum() <= 0) throw bad("Amount must be more than 0.");
        if (in.amount().stripTrailingZeros().scale() > 2) throw bad("Amount can have at most 2 decimal places.");
        if (in.amount().compareTo(MAX_AMOUNT) > 0) throw bad("Amount looks too large — please check it.");
        String method = in.method() == null ? "" : in.method().trim().toUpperCase();
        if (!METHODS.contains(method)) throw bad("Choose how it was paid: cash, M-Pesa, bank or cheque.");
        String ref = trim(in.reference(), 60);
        if (!method.equals("CASH") && ref == null) throw bad("Enter the " + (method.equals("MPESA") ? "M-Pesa code" : method.equals("CHEQUE") ? "cheque number" : "bank reference") + ".");

        Expense e = new Expense();
        e.setSpentOn(in.spentOn());
        e.setYearLabel(year(in.yearLabel()));
        e.setTerm(term(in.term()));
        e.setGroup(g);
        e.setDescription(desc);
        e.setPayee(trim(in.payee(), 100));
        e.setAmount(in.amount().setScale(2, RoundingMode.UNNECESSARY));
        e.setMethod(method);
        e.setReference(ref);
        e.setRecordedBy(who);
        e.setRecordedAt(LocalDateTime.now());
        expenseRepository.save(e);
        e.setVoucherNumber(String.format("PV-%d-%05d", in.spentOn().getYear(), e.getExpenseId()));
        expenseRepository.save(e);
        Map<String, Object> out = new LinkedHashMap<>(expenseMap(e));
        out.put("message", "Expense " + e.getVoucherNumber() + " recorded: " + g.getName() + " — " + e.getAmount().toPlainString() + ".");
        return out;
    }

    @Transactional
    public Map<String, Object> voidExpense(Long id, String reason, String who) {
        Expense e = expenseRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found."));
        if (e.isVoided()) throw bad("This expense is already voided.");
        String r = trim(reason, 200);
        if (r == null || r.length() < 3) throw bad("Say why it's being voided.");
        e.setVoided(true);
        e.setVoidReason(r);
        e.setVoidedBy(who);
        e.setVoidedAt(LocalDateTime.now());
        expenseRepository.save(e);
        return Map.of("message", e.getVoucherNumber() + " voided. It stays on record but no longer counts.");
    }

    // ══ Money in vs money out, per group, for one term ══════════════════════
    @Transactional(readOnly = true)
    public Map<String, Object> groupReport(String yearLabel, Integer termNo) {
        String yr = year(yearLabel);
        int t = term(termNo);
        int thisKey = Integer.parseInt(yr) * 10 + t;
        MoneyGroupService.Resolver res = moneyGroupService.resolver();

        // Every learner's charges (all terms — older terms are paid first)
        Map<Long, List<PaymentAllocator.Line>> byStudent = new HashMap<>();
        for (Invoice inv : invoiceRepository.findAllWithLines()) {
            int k = key(inv.getYearLabel(), inv.getTerm());
            long order = epoch(inv.getCreatedAt());
            List<PaymentAllocator.Line> list = byStudent.computeIfAbsent(inv.getStudent().getStudentId(), x -> new ArrayList<>());
            for (FeeItem li : inv.getLines()) {
                Long g = res.ofFeeItem(li.getName());
                list.add(new PaymentAllocator.Line(k, res.priority(g), order, g, li.getName(), li.getAmount()));
            }
        }
        for (ChargeEntry c : chargeEntryRepository.findAll()) {
            if (c.isCancelled()) continue;
            Long g = res.ofCharge(c.getCharge());
            byStudent.computeIfAbsent(c.getStudent().getStudentId(), x -> new ArrayList<>())
                    .add(new PaymentAllocator.Line(key(c.getYearLabel(), c.getTerm()), res.priority(g), epoch(c.getCreatedAt()), g, c.getName(), c.getAmount()));
        }
        for (TransportSubscription s : transportRepository.findAll()) {
            if (s.isCancelled()) continue;
            Long g = res.ofTransport();
            byStudent.computeIfAbsent(s.getStudent().getStudentId(), x -> new ArrayList<>())
                    .add(new PaymentAllocator.Line(key(s.getYearLabel(), s.getTerm()), res.priority(g), epoch(s.getCreatedAt()), g, "Transport — " + s.getRoute().getName(), s.getAmount()));
        }
        Map<Long, BigDecimal> paid = new HashMap<>();
        for (Object[] r : paymentRepository.totalsByStudent()) paid.put((Long) r[0], (BigDecimal) r[1]);

        // Split each learner's payments, keep this term's lines
        Map<Long, Acc> groups = new LinkedHashMap<>();
        for (MoneyGroup g : res.all()) groups.put(g.getGroupId(), new Acc(g));
        BigDecimal credit = BigDecimal.ZERO;
        int learners = 0;
        Set<Long> allStudents = new HashSet<>(byStudent.keySet());
        allStudents.addAll(paid.keySet());
        for (Long sid : allStudents) {
            List<PaymentAllocator.Line> lines = byStudent.getOrDefault(sid, List.of());
            PaymentAllocator.Result r = PaymentAllocator.allocate(lines, paid.getOrDefault(sid, BigDecimal.ZERO));
            credit = credit.add(r.credit());
            boolean inTerm = false;
            for (int i = 0; i < lines.size(); i++) {
                PaymentAllocator.Line l = lines.get(i);
                if (l.termKey() != thisKey) continue;
                inTerm = true;
                Acc a = groups.computeIfAbsent(l.groupId(), id -> new Acc(res.get(id)));
                a.add(l.item(), l.amount(), r.paid().get(i));
            }
            if (inTerm) learners++;
        }

        // Expenses of the term
        for (Expense e : expenseRepository.findByYearLabelAndTermOrderBySpentOnDescExpenseIdDesc(yr, t)) {
            if (e.isVoided()) continue;
            groups.computeIfAbsent(e.getGroup().getGroupId(), id -> new Acc(e.getGroup())).spent(e.getDescription(), e.getAmount());
        }

        List<Map<String, Object>> out = new ArrayList<>();
        BigDecimal tc = BigDecimal.ZERO, tp = BigDecimal.ZERO, te = BigDecimal.ZERO;
        for (Acc a : groups.values()) {
            if (a.charged.signum() == 0 && a.expenses.signum() == 0 && (a.group == null || !a.group.isActive())) continue;
            out.add(a.toMap());
            tc = tc.add(a.charged); tp = tp.add(a.collected); te = te.add(a.expenses);
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("charged", tc);
        totals.put("collected", tp);
        totals.put("owed", tc.subtract(tp));
        totals.put("expenses", te);
        totals.put("profit", tp.subtract(te));
        totals.put("profitIfAllPaid", tc.subtract(te));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("yearLabel", yr);
        result.put("term", t);
        result.put("groups", out);
        result.put("totals", totals);
        result.put("learners", learners);
        result.put("creditHeld", credit);   // paid in advance by learners (all terms), not yet used
        return result;
    }

    // ══ helpers ═════════════════════════════════════════════════════════════
    /** Running totals for one group in the report. */
    private static final class Acc {
        final MoneyGroup group;
        BigDecimal charged = BigDecimal.ZERO, collected = BigDecimal.ZERO, expenses = BigDecimal.ZERO;
        final Map<String, BigDecimal[]> items = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);   // name → [charged, collected]
        final Map<String, BigDecimal> spentOn = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        Acc(MoneyGroup g) { this.group = g; }

        void add(String item, BigDecimal amount, BigDecimal paid) {
            BigDecimal a = amount == null ? BigDecimal.ZERO : amount;
            charged = charged.add(a);
            collected = collected.add(paid);
            BigDecimal[] v = items.computeIfAbsent(item == null ? "" : item.trim(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            v[0] = v[0].add(a);
            v[1] = v[1].add(paid);
        }

        void spent(String what, BigDecimal amount) {
            expenses = expenses.add(amount);
            spentOn.merge(what.trim(), amount, BigDecimal::add);
        }

        Map<String, Object> toMap() {
            Map<String, Object> m;
            if (group != null) m = MoneyGroupService.groupMap(group);
            else {                                  // only when no money groups exist yet
                m = new LinkedHashMap<>();
                m.put("groupId", null);
                m.put("name", "Not grouped");
                m.put("color", "#999999");
            }
            m.put("charged", charged);
            m.put("collected", collected);
            m.put("owed", charged.subtract(collected));
            m.put("expenses", expenses);
            m.put("profit", collected.subtract(expenses));
            m.put("profitIfAllPaid", charged.subtract(expenses));
            List<Map<String, Object>> it = new ArrayList<>();
            items.forEach((k, v) -> it.add(Map.of("name", k, "charged", v[0], "collected", v[1])));
            m.put("items", it);
            List<Map<String, Object>> sp = new ArrayList<>();
            spentOn.forEach((k, v) -> sp.add(Map.of("description", k, "amount", v)));
            m.put("spending", sp);
            return m;
        }
    }

    private Map<String, Object> expenseList(List<Expense> list) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        for (String m : List.of("CASH", "MPESA", "BANK", "CHEQUE")) byMethod.put(m, BigDecimal.ZERO);
        Map<Long, Map<String, Object>> byGroup = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Expense e : list) {
            rows.add(expenseMap(e));
            if (e.isVoided()) continue;
            total = total.add(e.getAmount());
            byMethod.merge(e.getMethod(), e.getAmount(), BigDecimal::add);
            Map<String, Object> g = byGroup.computeIfAbsent(e.getGroup().getGroupId(), id -> {
                Map<String, Object> m = MoneyGroupService.groupMap(e.getGroup());
                m.put("total", BigDecimal.ZERO);
                return m;
            });
            g.put("total", ((BigDecimal) g.get("total")).add(e.getAmount()));
        }
        return Map.of("expenses", rows, "total", total, "byMethod", byMethod, "byGroup", new ArrayList<>(byGroup.values()));
    }

    private static Map<String, Object> expenseMap(Expense e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expenseId", e.getExpenseId());
        m.put("voucherNumber", e.getVoucherNumber());
        m.put("spentOn", e.getSpentOn().toString());
        m.put("yearLabel", e.getYearLabel());
        m.put("term", e.getTerm());
        m.put("groupId", e.getGroup().getGroupId());
        m.put("groupName", e.getGroup().getName());
        m.put("groupColor", e.getGroup().getColor());
        m.put("description", e.getDescription());
        m.put("payee", e.getPayee());
        m.put("amount", e.getAmount());
        m.put("method", e.getMethod());
        m.put("reference", e.getReference());
        m.put("recordedBy", e.getRecordedBy());
        m.put("voided", e.isVoided());
        m.put("voidReason", e.getVoidReason());
        m.put("voidedBy", e.getVoidedBy());
        return m;
    }

    private static int key(String yearLabel, Integer term) {
        try { return Integer.parseInt(String.valueOf(yearLabel).trim()) * 10 + (term == null ? 0 : term); }
        catch (NumberFormatException e) { return 0; }
    }

    private static long epoch(LocalDateTime t) { return t == null ? 0 : t.toEpochSecond(ZoneOffset.UTC); }

    private static String year(String y) {
        String v = y == null ? "" : y.trim();
        if (!v.matches("\\d{4}")) throw bad("Year must be 4 digits, e.g. 2026.");
        return v;
    }

    private static int term(Integer t) {
        if (t == null || t < 1 || t > 3) throw bad("Term must be 1, 2 or 3.");
        return t;
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
