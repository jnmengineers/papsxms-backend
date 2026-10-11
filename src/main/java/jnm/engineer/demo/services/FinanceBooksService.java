package jnm.engineer.demo.services;

import jnm.engineer.demo.models.*;
import jnm.engineer.demo.models.ChargeEntry;
import jnm.engineer.demo.models.Cheque;
import jnm.engineer.demo.models.Expense;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.models.FeeItem;
import jnm.engineer.demo.models.Invoice;
import jnm.engineer.demo.models.MoneyGroup;
import jnm.engineer.demo.models.MpesaFloatEntry;
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
    private final jnm.engineer.demo.security.RoleGuard roleGuard;
    private final ChequeRepository chequeRepository;
    private final MpesaFloatEntryRepository floatRepository;
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
    public Map<String, Object> recordExpense(ExpenseIn in, String who) { return recordExpense(in, who, null); }

    /**
     * approvedBy: already approved elsewhere (e.g. a payroll run the treasurer approved) — recorded as approved.
     * Otherwise, once the school has a treasurer, the bill waits for the treasurer's approval.
     */
    @Transactional
    public Map<String, Object> recordExpense(ExpenseIn in, String who, String approvedBy) {
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
        boolean needsTreasurer = approvedBy == null && roleGuard.treasurerApprovalOn() && !roleGuard.current(User.Role.TREASURER);
        String method, ref;
        if (needsTreasurer) {
            // A bill for the treasurer: approved first, then paid by the treasurer's cheque
            if (trim(in.payee(), 100) == null) throw bad("Enter who is to be paid — the payee on the cheque.");
            method = "CHEQUE";
            ref = null;
        } else {
            method = in.method() == null ? "" : in.method().trim().toUpperCase();
            if (!METHODS.contains(method)) throw bad("Choose how it was paid: cash, M-Pesa, bank or cheque.");
            ref = trim(in.reference(), 60);
            if (!method.equals("CASH") && ref == null) throw bad("Enter the " + (method.equals("MPESA") ? "M-Pesa code" : method.equals("CHEQUE") ? "cheque number" : "bank reference") + ".");
        }

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
        e.setApprovalStatus(needsTreasurer ? "PENDING" : "APPROVED");
        if (!needsTreasurer) { e.setApprovedBy(approvedBy != null ? approvedBy : who); e.setApprovedAt(LocalDateTime.now()); }
        e.setPaid(!needsTreasurer);                          // bills wait for the treasurer's cheque
        if (!needsTreasurer && "MPESA".equals(method) && floatInUse()) e.setFromFloat(true);   // paid from the school's M-Pesa line
        e.setPaidOn(needsTreasurer ? null : in.spentOn());
        expenseRepository.save(e);
        e.setVoucherNumber(String.format("PV-%d-%05d", in.spentOn().getYear(), e.getExpenseId()));
        expenseRepository.save(e);
        Map<String, Object> out = new LinkedHashMap<>(expenseMap(e));
        out.put("message", "Expense " + e.getVoucherNumber() + " recorded: " + g.getName() + " — " + e.getAmount().toPlainString() + "."
                + (needsTreasurer ? " It is on the list for the treasurer to approve and pay by cheque; it counts once paid." : ""));
        return out;
    }

    // ── Treasurer approval ──
    @Transactional(readOnly = true)
    public Map<String, Object> pendingExpenses() {
        List<Expense> list = expenseRepository.findAll().stream().filter(e -> "PENDING".equals(e.getApprovalStatus()) && !e.isVoided())
                .sorted(Comparator.comparing(Expense::getSpentOn)).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expenses", list.stream().map(FinanceBooksService::expenseMap).toList());
        m.put("total", list.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        m.put("approvalOn", roleGuard.treasurerApprovalOn());
        return m;
    }

    @Transactional
    public Map<String, Object> approveExpense(Long id, String who) {
        Expense e = expenseRepository.findById(id).orElseThrow(() -> bad("Expense not found."));
        if (!"PENDING".equals(e.getApprovalStatus()) || e.isVoided()) throw bad(e.getVoucherNumber() + " is not waiting for approval.");
        if (who != null && who.equalsIgnoreCase(e.getRecordedBy())) throw bad("You recorded this bill, so someone else must approve it.");
        e.setApprovalStatus("APPROVED");
        e.setApprovedBy(who);
        e.setApprovedAt(LocalDateTime.now());
        e.setRejectReason(null);
        expenseRepository.save(e);
        return Map.of("message", e.getVoucherNumber() + " approved — " + e.getAmount().toPlainString() + " now counts in the books.");
    }

    @Transactional
    public Map<String, Object> rejectExpense(Long id, String reason, String who) {
        Expense e = expenseRepository.findById(id).orElseThrow(() -> bad("Expense not found."));
        if (!"PENDING".equals(e.getApprovalStatus()) || e.isVoided()) throw bad(e.getVoucherNumber() + " is not waiting for approval.");
        String r = trim(reason, 200);
        if (r == null || r.length() < 3) throw bad("Say why the bill is rejected, so the bursar can put it right.");
        e.setApprovalStatus("REJECTED");
        e.setApprovedBy(who);
        e.setApprovedAt(LocalDateTime.now());
        e.setRejectReason(r);
        expenseRepository.save(e);
        return Map.of("message", e.getVoucherNumber() + " rejected. The bursar can see why.");
    }

    /** Approve several bills at once (the treasurer ticks them on the list). */
    @Transactional
    public Map<String, Object> approveMany(List<Long> ids, String who) {
        if (ids == null || ids.isEmpty()) throw bad("Tick the bills to approve.");
        BigDecimal total = BigDecimal.ZERO;
        for (Long id : ids) {
            approveExpense(id, who);
            total = total.add(expenseRepository.findById(id).map(Expense::getAmount).orElse(BigDecimal.ZERO));
        }
        return Map.of("message", ids.size() + " bill(s) approved — " + total.toPlainString() + ". Raise cheques to pay them.");
    }

    /** Approved bills not yet paid: the treasurer pays these by cheque. */
    @Transactional(readOnly = true)
    public Map<String, Object> toPay() {
        List<Expense> list = expenseRepository.findAll().stream()
                .filter(e -> !e.isVoided() && "APPROVED".equals(e.getApprovalStatus()) && Boolean.FALSE.equals(e.getPaid()))
                .sorted(Comparator.comparing((Expense e) -> String.valueOf(e.getPayee()).toUpperCase()).thenComparing(Expense::getSpentOn)).toList();
        return Map.of("expenses", list.stream().map(FinanceBooksService::expenseMap).toList(),
                "total", list.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public record ChequeIn(String chequeNumber, LocalDate chequeDate, String payee, String memo, List<Long> expenseIds) {}

    /** The treasurer raises a cheque for one or more approved bills; they become paid (and count) on the cheque date. */
    @Transactional
    public Map<String, Object> raiseCheque(ChequeIn in, String who) {
        String number = trim(in == null ? null : in.chequeNumber(), 30);
        if (number == null) throw bad("Enter the cheque number.");
        if (in.chequeDate() == null) throw bad("Enter the date on the cheque.");
        if (in.chequeDate().isAfter(LocalDate.now().plusDays(90))) throw bad("The cheque date looks too far ahead — please check it.");
        String payee = trim(in.payee(), 100);
        if (payee == null) throw bad("Enter the payee — who the cheque is made out to.");
        if (in.expenseIds() == null || in.expenseIds().isEmpty()) throw bad("Tick the bills this cheque pays.");
        // A cheque number is used once, ever — a spoilt or stopped cheque keeps its number (like the leaf in the chequebook)
        if (chequeRepository.existsByChequeNumberIgnoreCase(number)) throw bad("Cheque number " + number + " has already been used (cancelled cheques keep their numbers). Use the next cheque in the book.");
        List<Expense> bills = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Long id : new LinkedHashSet<>(in.expenseIds())) {
            Expense e = expenseRepository.findById(id).orElseThrow(() -> bad("A bill was not found — refresh the page."));
            if (e.isVoided() || !"APPROVED".equals(e.getApprovalStatus()) || !Boolean.FALSE.equals(e.getPaid()))
                throw bad(e.getVoucherNumber() + " is not an approved bill waiting to be paid.");
            bills.add(e);
            total = total.add(e.getAmount());
        }
        Cheque c = new Cheque();
        c.setChequeNumber(number);
        c.setChequeDate(in.chequeDate());
        c.setPayee(payee);
        c.setAmount(total);
        c.setMemo(trim(in.memo(), 200));
        c.setRaisedBy(who);
        c.setRaisedAt(LocalDateTime.now());
        chequeRepository.save(c);
        for (Expense e : bills) {
            e.setPaid(true);
            e.setPaidOn(in.chequeDate());
            e.setMethod("CHEQUE");
            e.setReference(number);
            e.setChequeId(c.getChequeId());
            expenseRepository.save(e);
        }
        Map<String, Object> out = new LinkedHashMap<>(chequeMap(c, bills));
        out.put("message", "Cheque " + number + " to " + payee + " for " + total.toPlainString() + " raised — " + bills.size() + " bill(s) now paid.");
        return out;
    }

    /** Spoilt or stopped cheque: its bills go back to "to be paid". */
    @Transactional
    public Map<String, Object> cancelCheque(Long id, String reason, String who) {
        Cheque c = chequeRepository.findById(id).orElseThrow(() -> bad("Cheque not found."));
        if (c.isCancelled()) throw bad("Cheque " + c.getChequeNumber() + " is already cancelled.");
        String r = trim(reason, 200);
        if (r == null || r.length() < 3) throw bad("Say why the cheque is cancelled (e.g. spoilt, stopped).");
        if ("FLOAT".equals(c.getPurpose())) {
            MpesaFloatEntry top = floatRepository.findByChequeIdAndCancelledFalse(c.getChequeId()).orElse(null);
            if (top != null) {
                BigDecimal after = floatBalance().subtract(top.getAmount());
                if (after.signum() < 0) throw bad("Payments were already made from this money — the M-Pesa line would go below zero ("
                        + after.toPlainString() + "). Record a float adjustment instead.");
                top.setCancelled(true);
                floatRepository.save(top);
            }
        }
        int n = 0;
        for (Expense e : expenseRepository.findAll()) {
            if (!c.getChequeId().equals(e.getChequeId())) continue;
            e.setPaid(false);
            e.setPaidOn(null);
            e.setReference(null);
            e.setChequeId(null);
            expenseRepository.save(e);
            n++;
        }
        c.setCancelled(true);
        c.setCancelReason(r);
        c.setCancelledBy(who);
        c.setCancelledAt(LocalDateTime.now());
        chequeRepository.save(c);
        return Map.of("message", "Cheque " + c.getChequeNumber() + " cancelled. " + n + " bill(s) are back on the list to be paid.");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cheques(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw bad("Choose the dates.");
        List<Expense> all = expenseRepository.findAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Cheque c : chequeRepository.findByChequeDateBetweenOrderByChequeDateDescChequeIdDesc(from, to)) {
            rows.add(chequeMap(c, all.stream().filter(e -> c.getChequeId().equals(e.getChequeId())).toList()));
            if (!c.isCancelled()) total = total.add(c.getAmount());
        }
        return Map.of("cheques", rows, "total", total);
    }

    // ══ Bank reconciliation: find expenses by the codes on the statement (whatever date was recorded) ══
    @Transactional(readOnly = true)
    public List<Map<String, Object>> expensesByCodes(List<String> codes) {
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (String raw : codes == null ? List.<String>of() : codes) {
            String code = raw == null ? "" : raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
            if (code.isEmpty() || out.size() > 1000) continue;
            String noZeros = code.replaceFirst("^0+", "");
            for (String c : new LinkedHashSet<>(List.of(code, noZeros.isEmpty() ? code : noZeros))) {
                for (Expense e : expenseRepository.findByReferenceStartingWithIgnoreCaseAndVoidedFalse(c)) {
                    String ref = e.getReference() == null ? "" : e.getReference().toUpperCase().replaceAll("[^A-Z0-9]", "");
                    boolean hit = ref.equals(c) || ref.replaceFirst("^0+", "").equals(noZeros) || (c.length() >= 6 && ref.startsWith(c));
                    if (hit && e.counts() && !out.containsKey(e.getExpenseId())) out.put(e.getExpenseId(), expenseMap(e));
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    // ══ Bank reconciliation: mark records as confirmed on the bank statement ══
    public record BankLineIn(LocalDate bankDate, String particulars, BigDecimal amount, List<Long> paymentIds, List<Long> expenseIds) {}
    public record BankConfirmIn(List<BankLineIn> lines) {}
    public record BankUndoIn(List<Long> paymentIds, List<Long> expenseIds) {}

    /** Marks the matched payments/expenses as seen on the bank statement (date + the statement line). */
    @Transactional
    public Map<String, Object> confirmInBank(BankConfirmIn in, String who) {
        if (in == null || in.lines() == null || in.lines().isEmpty()) throw bad("Nothing to confirm.");
        int pays = 0, exps = 0;
        LocalDateTime now = LocalDateTime.now();
        for (BankLineIn l : in.lines()) {
            if (l.bankDate() == null) throw bad("A statement line has no date.");
            String line = trim((l.bankDate() + " " + (l.particulars() == null ? "" : l.particulars()) + (l.amount() != null ? " " + l.amount().toPlainString() : "")), 200);
            for (Long id : l.paymentIds() == null ? List.<Long>of() : l.paymentIds()) {
                Payment p = paymentRepository.findById(id).orElseThrow(() -> bad("A payment was not found — check the statement again."));
                if (p.isReversed()) throw bad(p.getReceiptNumber() + " was reversed — it can't be confirmed in the bank.");
                p.setBankConfirmedOn(l.bankDate()); p.setBankLine(line); p.setBankConfirmedBy(who); p.setBankConfirmedAt(now);
                paymentRepository.save(p);
                pays++;
            }
            for (Long id : l.expenseIds() == null ? List.<Long>of() : l.expenseIds()) {
                Expense e = expenseRepository.findById(id).orElseThrow(() -> bad("An expense was not found — check the statement again."));
                if (e.isVoided()) throw bad(e.getVoucherNumber() + " was voided — it can't be confirmed in the bank.");
                e.setBankConfirmedOn(l.bankDate()); e.setBankLine(line); e.setBankConfirmedBy(who); e.setBankConfirmedAt(now);
                expenseRepository.save(e);
                exps++;
            }
        }
        return Map.of("message", pays + " payment(s) and " + exps + " expense(s) marked as confirmed in the bank.");
    }

    @Transactional
    public Map<String, Object> undoBankConfirm(BankUndoIn in) {
        int n = 0;
        for (Long id : in == null || in.paymentIds() == null ? List.<Long>of() : in.paymentIds()) {
            Payment p = paymentRepository.findById(id).orElse(null);
            if (p == null) continue;
            p.setBankConfirmedOn(null); p.setBankLine(null); p.setBankConfirmedBy(null); p.setBankConfirmedAt(null);
            paymentRepository.save(p); n++;
        }
        for (Long id : in == null || in.expenseIds() == null ? List.<Long>of() : in.expenseIds()) {
            Expense e = expenseRepository.findById(id).orElse(null);
            if (e == null) continue;
            e.setBankConfirmedOn(null); e.setBankLine(null); e.setBankConfirmedBy(null); e.setBankConfirmedAt(null);
            expenseRepository.save(e); n++;
        }
        return Map.of("message", n + " record(s) no longer marked as confirmed in the bank.");
    }

    // ══ M-Pesa float: the school's M-Pesa line, topped up by cheque, used to pay suppliers ══
    private boolean floatInUse() { return !floatRepository.findByCancelledFalse().isEmpty(); }

    /** Money on the M-Pesa line: top-ups and adjustments, less bills paid from it. */
    @Transactional(readOnly = true)
    public BigDecimal floatBalance() {
        BigDecimal in = floatRepository.findByCancelledFalse().stream().map(MpesaFloatEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal out = expenseRepository.findAll().stream().filter(e -> Boolean.TRUE.equals(e.getFromFloat()) && !e.isVoided() && e.isPaidOut())
                .map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return in.subtract(out);
    }

    public record FloatChequeIn(String chequeNumber, LocalDate chequeDate, String payee, BigDecimal amount, String memo) {}

    /** The treasurer's cheque to withdraw cash for the M-Pesa line. A transfer between the school's own accounts — not an expense. */
    @Transactional
    public Map<String, Object> raiseFloatCheque(FloatChequeIn in, String who) {
        String number = trim(in == null ? null : in.chequeNumber(), 30);
        if (number == null) throw bad("Enter the cheque number.");
        if (in.chequeDate() == null) throw bad("Enter the date on the cheque.");
        if (in.chequeDate().isAfter(LocalDate.now().plusDays(90))) throw bad("The cheque date looks too far ahead — please check it.");
        BigDecimal amount = in.amount();
        if (amount == null || amount.signum() <= 0) throw bad("Enter the amount of the cheque.");
        if (amount.stripTrailingZeros().scale() > 2) throw bad("The amount can have at most 2 decimal places.");
        if (chequeRepository.existsByChequeNumberIgnoreCase(number)) throw bad("Cheque number " + number + " has already been used (cancelled cheques keep their numbers). Use the next cheque in the book.");
        Cheque c = new Cheque();
        c.setChequeNumber(number);
        c.setChequeDate(in.chequeDate());
        c.setPayee(trim(in.payee(), 100) != null ? trim(in.payee(), 100) : "CASH — M-PESA FLOAT");
        c.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        c.setMemo(trim(in.memo(), 200));
        c.setPurpose("FLOAT");
        c.setRaisedBy(who);
        c.setRaisedAt(LocalDateTime.now());
        chequeRepository.save(c);
        MpesaFloatEntry top = new MpesaFloatEntry();
        top.setEntryDate(in.chequeDate());
        top.setKind("TOPUP");
        top.setAmount(c.getAmount());
        top.setChequeId(c.getChequeId());
        top.setNote("Cheque " + number);
        top.setRecordedBy(who);
        top.setRecordedAt(LocalDateTime.now());
        floatRepository.save(top);
        Map<String, Object> out = new LinkedHashMap<>(chequeMap(c, List.of()));
        out.put("message", "Cheque " + number + " for " + c.getAmount().toPlainString() + " raised for the M-Pesa line. Float balance now " + floatBalance().toPlainString() + ".");
        return out;
    }

    public record FloatAdjustIn(LocalDate date, BigDecimal amount, String note) {}

    /** Opening balance of the M-Pesa line, or a correction (negative takes money off). */
    @Transactional
    public Map<String, Object> adjustFloat(FloatAdjustIn in, String who) {
        if (in == null || in.date() == null) throw bad("Enter the date.");
        if (in.amount() == null || in.amount().signum() == 0) throw bad("Enter the amount (negative to take money off).");
        String note = trim(in.note(), 200);
        if (note == null || note.length() < 3) throw bad("Say what this is — e.g. \"Opening balance\" or \"M-Pesa statement correction\".");
        MpesaFloatEntry e = new MpesaFloatEntry();
        e.setEntryDate(in.date());
        e.setKind("ADJUST");
        e.setAmount(in.amount().setScale(2, RoundingMode.HALF_UP));
        e.setNote(note);
        e.setRecordedBy(who);
        e.setRecordedAt(LocalDateTime.now());
        floatRepository.save(e);
        return Map.of("message", "M-Pesa line adjusted by " + e.getAmount().toPlainString() + ". Balance now " + floatBalance().toPlainString() + ".");
    }

    public record MpesaPayIn(List<Long> expenseIds, String mpesaCode, LocalDate paidOn, BigDecimal charge) {}

    /** Pay approved bills from the M-Pesa line. The M-Pesa charge (if any) is recorded as its own expense. */
    @Transactional
    public Map<String, Object> payByMpesa(MpesaPayIn in, String who) {
        if (in == null || in.expenseIds() == null || in.expenseIds().isEmpty()) throw bad("Tick the bills this M-Pesa payment pays.");
        String code = in.mpesaCode() == null ? null : in.mpesaCode().trim().toUpperCase();
        if (code == null || !code.matches("[A-Z0-9]{6,20}")) throw bad("Enter the M-Pesa code from the confirmation message (e.g. SGH71K2XYZ).");
        if (in.paidOn() == null) throw bad("Enter the date it was paid.");
        if (in.paidOn().isAfter(LocalDate.now())) throw bad("The payment date can't be in the future.");
        BigDecimal charge = in.charge() == null ? BigDecimal.ZERO : in.charge();
        if (charge.signum() < 0 || charge.stripTrailingZeros().scale() > 2) throw bad("Check the M-Pesa charge.");
        boolean codeUsed = expenseRepository.findAll().stream().anyMatch(e -> !e.isVoided() && code.equalsIgnoreCase(e.getReference()) && "MPESA".equals(e.getMethod()));
        if (codeUsed) throw bad("M-Pesa code " + code + " is already recorded.");
        List<Expense> bills = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Long id : new LinkedHashSet<>(in.expenseIds())) {
            Expense e = expenseRepository.findById(id).orElseThrow(() -> bad("A bill was not found — refresh the page."));
            if (e.isVoided() || !"APPROVED".equals(e.getApprovalStatus()) || !Boolean.FALSE.equals(e.getPaid()))
                throw bad(e.getVoucherNumber() + " is not an approved bill waiting to be paid.");
            bills.add(e);
            total = total.add(e.getAmount());
        }
        BigDecimal balance = floatBalance();
        if (total.add(charge).compareTo(balance) > 0) {
            throw bad("The M-Pesa line has only " + balance.toPlainString() + " but this payment needs " + total.add(charge).toPlainString()
                    + ". The treasurer should raise a float cheque to top it up first.");
        }
        for (Expense e : bills) {
            e.setPaid(true);
            e.setPaidOn(in.paidOn());
            e.setMethod("MPESA");
            e.setReference(code);
            e.setFromFloat(true);
            expenseRepository.save(e);
        }
        String chargeNo = null;
        if (charge.signum() > 0) {                              // the M-Pesa charge is a real cost of paying
            Expense first = bills.get(0);
            Expense f = new Expense();
            f.setSpentOn(in.paidOn());
            f.setYearLabel(first.getYearLabel());
            f.setTerm(first.getTerm());
            f.setGroup(first.getGroup());
            f.setDescription("M-Pesa charges — payment " + code);
            f.setPayee("Safaricom (M-Pesa)");
            f.setAmount(charge.setScale(2, RoundingMode.HALF_UP));
            f.setMethod("MPESA");
            f.setReference(code);
            f.setRecordedBy(who);
            f.setRecordedAt(LocalDateTime.now());
            f.setApprovalStatus("APPROVED");
            f.setApprovedBy(who);
            f.setApprovedAt(LocalDateTime.now());
            f.setPaid(true);
            f.setPaidOn(in.paidOn());
            f.setFromFloat(true);
            expenseRepository.save(f);
            f.setVoucherNumber(String.format("PV-%d-%05d", in.paidOn().getYear(), f.getExpenseId()));
            expenseRepository.save(f);
            chargeNo = f.getVoucherNumber();
        }
        return Map.of("message", bills.size() + " bill(s) paid by M-Pesa " + code + " — " + total.toPlainString()
                + (chargeNo != null ? " (charge " + charge.toPlainString() + " recorded as " + chargeNo + ")" : "")
                + ". M-Pesa line balance now " + floatBalance().toPlainString() + ".");
    }

    /** The M-Pesa line's statement: top-ups and adjustments in, bill payments out, running balance. */
    @Transactional(readOnly = true)
    public Map<String, Object> floatStatement(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw bad("Choose the dates.");
        record Row(LocalDate date, String kind, String detail, String ref, BigDecimal amount, String by) {}
        List<Row> all = new ArrayList<>();
        Map<Long, String> chequeNo = new HashMap<>();
        chequeRepository.findAll().forEach(c -> chequeNo.put(c.getChequeId(), c.getChequeNumber()));
        for (MpesaFloatEntry f : floatRepository.findByCancelledFalse()) {
            all.add(new Row(f.getEntryDate(), f.getKind(), f.getNote(), f.getChequeId() != null ? chequeNo.get(f.getChequeId()) : "", f.getAmount(), f.getRecordedBy()));
        }
        for (Expense e : expenseRepository.findAll()) {
            if (!Boolean.TRUE.equals(e.getFromFloat()) || e.isVoided() || !e.isPaidOut()) continue;
            LocalDate d = e.getPaidOn() != null ? e.getPaidOn() : e.getSpentOn();
            all.add(new Row(d, "PAYMENT", e.getVoucherNumber() + " · " + (e.getPayee() == null ? "" : e.getPayee() + " — ") + e.getDescription(), e.getReference(), e.getAmount().negate(), e.getRecordedBy()));
        }
        all.sort(Comparator.comparing(Row::date).thenComparing(r -> r.amount().signum() < 0 ? 1 : 0));
        BigDecimal balance = BigDecimal.ZERO;
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal opening = null, in = BigDecimal.ZERO, out = BigDecimal.ZERO;
        for (Row r : all) {
            if (r.date().isBefore(from)) { balance = balance.add(r.amount()); continue; }
            if (opening == null) opening = balance;
            if (r.date().isAfter(to)) break;
            balance = balance.add(r.amount());
            if (r.amount().signum() > 0) in = in.add(r.amount()); else out = out.add(r.amount().negate());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", r.date().toString());
            m.put("kind", r.kind());
            m.put("detail", r.detail());
            m.put("reference", r.ref());
            m.put("amount", r.amount());
            m.put("balance", balance);
            m.put("by", r.by());
            rows.add(m);
        }
        if (opening == null) opening = balance;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("opening", opening);
        m.put("moneyIn", in);
        m.put("moneyOut", out);
        m.put("closing", opening.add(in).subtract(out));
        m.put("balanceNow", floatBalance());
        m.put("rows", rows);
        return m;
    }

    private static Map<String, Object> chequeMap(Cheque c, List<Expense> bills) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chequeId", c.getChequeId());
        m.put("chequeNumber", c.getChequeNumber());
        m.put("chequeDate", c.getChequeDate().toString());
        m.put("payee", c.getPayee());
        m.put("amount", c.getAmount());
        m.put("memo", c.getMemo());
        m.put("purpose", c.getPurpose() == null ? "BILLS" : c.getPurpose());
        m.put("raisedBy", c.getRaisedBy());
        m.put("cancelled", c.isCancelled());
        m.put("cancelReason", c.getCancelReason());
        m.put("cancelledBy", c.getCancelledBy());
        m.put("bills", bills.stream().map(FinanceBooksService::expenseMap).toList());
        return m;
    }

    @Transactional
    public Map<String, Object> voidExpense(Long id, String reason, String who) {
        Expense e = expenseRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found."));
        if (e.isVoided()) throw bad("This expense is already voided.");
        if (e.getChequeId() != null) throw bad(e.getVoucherNumber() + " was paid by cheque " + e.getReference() + " — cancel the cheque first.");
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
            if (!e.counts()) continue;                      // voided, awaiting the treasurer, or rejected
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
            if (!e.counts()) continue;                      // voided, awaiting the treasurer, or rejected
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
        m.put("approvalStatus", e.getApprovalStatus() == null ? "APPROVED" : e.getApprovalStatus());
        m.put("paid", e.isPaidOut());
        m.put("paidOn", e.getPaidOn() != null ? e.getPaidOn().toString() : null);
        m.put("chequeId", e.getChequeId());
        m.put("approvedBy", e.getApprovedBy());
        m.put("approvedAt", e.getApprovedAt() != null ? e.getApprovedAt().toString() : null);
        m.put("rejectReason", e.getRejectReason());
        m.put("bankConfirmedOn", e.getBankConfirmedOn() != null ? e.getBankConfirmedOn().toString() : null);
        m.put("bankLine", e.getBankLine());
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
