package jnm.engineer.demo.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jnm.engineer.demo.models.PayrollRun;
import jnm.engineer.demo.models.PayrollSettings;
import jnm.engineer.demo.models.Payslip;
import jnm.engineer.demo.models.StaffMember;
import jnm.engineer.demo.models.StaffPayItem;
import jnm.engineer.demo.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Payroll: staff, regular allowances/deductions, statutory rates, and monthly pay runs.
 * Rates live in the database (Payroll → Rates) and start from default-settings.json → "payroll".
 * Each run keeps a copy of the rates it used; payslips copy the staff details and figures,
 * so history never changes. The money itself is calculated by PayrollCalculator.
 */
@Service
@RequiredArgsConstructor
public class PayrollService {
    private static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Set<String> CATEGORIES = Set.of("TEACHING", "SUPPORT", "ADMINISTRATION");
    private static final Set<String> METHODS = Set.of("BANK", "MPESA", "CASH", "CHEQUE");
    private static final BigDecimal MAX = new BigDecimal("10000000");

    private final StaffMemberRepository staffRepository;
    private final StaffPayItemRepository itemRepository;
    private final PayrollSettingsRepository settingsRepository;
    private final PayrollRunRepository runRepository;
    private final PayslipRepository payslipRepository;
    private final FinanceBooksService booksService;
    private final jnm.engineer.demo.security.RoleGuard roleGuard;
    private final CurrentTermService currentTermService;
    private final MoneyGroupRepository groupRepository;

    public record ItemIn(String kind, String name, BigDecimal amount, Boolean taxable) {}
    public record StaffIn(Long staffId, String staffNumber, String fullName, String jobTitle, String category, Long teacherId,
                          String phone, String kraPin, String nssfNumber, String shaNumber, String payMethod, String bankName,
                          String bankBranch, String accountNumber, String bankCode, BigDecimal basicSalary, Boolean statutory, Boolean active,
                          LocalDate startDate, String notes, List<ItemIn> items) {}
    public record PreviewIn(BigDecimal basicSalary, Boolean statutory, List<ItemIn> items) {}
    public record PayIn(LocalDate paidOn, Long groupId, String yearLabel, Integer term, String method, String reference) {}

    // ══ Rates ═══════════════════════════════════════════════════════════════
    @Transactional
    public Map<String, Object> rates() {
        return readJson(currentRatesJson());
    }

    @Transactional
    public Map<String, Object> saveRates(Map<String, Object> in, String who) {
        String json = toJson(in);
        toRates(json);                                          // validates (throws a clear message if wrong)
        PayrollSettings s = settingsRepository.findById(1L).orElseGet(PayrollSettings::new);
        s.setId(1L);
        s.setRatesJson(json);
        s.setUpdatedBy(who);
        s.setUpdatedAt(LocalDateTime.now());
        settingsRepository.save(s);
        return Map.of("message", "Rates saved. They apply to new pay runs, and to draft runs when you press Recalculate.");
    }

    private String currentRatesJson() {
        return settingsRepository.findById(1L).map(PayrollSettings::getRatesJson).orElseGet(() -> {
            String seed = seedRatesJson();
            PayrollSettings s = new PayrollSettings();
            s.setId(1L);
            s.setRatesJson(seed);
            s.setUpdatedBy("default-settings.json");
            s.setUpdatedAt(LocalDateTime.now());
            settingsRepository.save(s);
            return seed;
        });
    }

    private static String seedRatesJson() {
        try (InputStream in = new ClassPathResource("default-settings.json").getInputStream()) {
            JsonNode p = JSON.readTree(in).get("payroll");
            if (p == null) throw new IllegalStateException("no \"payroll\" in default-settings.json");
            return JSON.writeValueAsString(p);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Payroll rates are not set up: " + e.getMessage());
        }
    }

    /** JSON → calculator rates, with checks so a typing mistake can't produce wrong pay. */
    static PayrollCalculator.Rates toRates(String json) {
        try {
            JsonNode n = JSON.readTree(json);
            List<PayrollCalculator.Band> bands = new ArrayList<>();
            BigDecimal last = BigDecimal.ZERO;
            JsonNode arr = n.get("payeBands");
            if (arr == null || !arr.isArray() || arr.isEmpty()) throw bad("Enter the PAYE bands.");
            for (int i = 0; i < arr.size(); i++) {
                JsonNode b = arr.get(i);
                boolean lastBand = i == arr.size() - 1;
                BigDecimal upTo = b.hasNonNull("upTo") && !b.get("upTo").asText().isBlank() ? new BigDecimal(b.get("upTo").asText()) : null;
                BigDecimal rate = num(b, "rate", "PAYE band rate");
                if (rate.compareTo(BigDecimal.valueOf(100)) > 0) throw bad("A PAYE rate must be between 0 and 100%.");
                if (upTo == null && !lastBand) throw bad("Only the last PAYE band can have no upper limit.");
                if (upTo != null && lastBand) throw bad("The last PAYE band must have no upper limit.");
                if (upTo != null && upTo.compareTo(last) <= 0) throw bad("PAYE bands must go up in order.");
                bands.add(new PayrollCalculator.Band(upTo, rate));
                if (upTo != null) last = upTo;
            }
            return new PayrollCalculator.Rates(bands, num(n, "personalRelief", "personal relief"),
                    num(n, "nssfRate", "NSSF rate"), optNum(n, "nssfUpperLimit"),
                    num(n, "shifRate", "SHIF rate"), optNum(n, "shifMinimum"),
                    num(n, "housingRate", "Housing Levy rate"), num(n, "housingEmployerRate", "employer Housing Levy rate"),
                    !n.has("nssfBeforePaye") || n.get("nssfBeforePaye").asBoolean(),
                    !n.has("shifBeforePaye") || n.get("shifBeforePaye").asBoolean(),
                    !n.has("housingBeforePaye") || n.get("housingBeforePaye").asBoolean());
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw bad("The rates are not valid: " + e.getMessage()); }
    }
    private static BigDecimal num(JsonNode n, String f, String label) {
        if (!n.hasNonNull(f) || n.get(f).asText().isBlank()) throw bad("Enter the " + label + ".");
        BigDecimal v = new BigDecimal(n.get(f).asText());
        if (v.signum() < 0) throw bad("The " + label + " can't be negative.");
        return v;
    }
    private static BigDecimal optNum(JsonNode n, String f) {
        return n.hasNonNull(f) && !n.get(f).asText().isBlank() ? new BigDecimal(n.get(f).asText()) : null;
    }

    // ══ Staff ═══════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public List<Map<String, Object>> staff() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (StaffMember s : staffRepository.findAllByOrderByActiveDescFullNameAsc()) {
            if (s.isOnPayroll()) out.add(staffMap(s, itemRepository.findByStaffStaffId(s.getStaffId())));   // TSC staff are not paid by the school
        }
        return out;
    }

    @Transactional
    public Map<String, Object> saveStaff(StaffIn in) {
        StaffMember s = in.staffId() == null ? new StaffMember()
                : staffRepository.findById(in.staffId()).orElseThrow(() -> notFound("Staff member not found."));
        String number = trim(in.staffNumber(), 20);
        String name = trim(in.fullName(), 100);
        if (number == null) throw bad("Enter the staff number.");
        if (name == null || name.length() < 3) throw bad("Enter the full name.");
        boolean dup = in.staffId() == null ? staffRepository.existsByStaffNumberIgnoreCase(number)
                : staffRepository.existsByStaffNumberIgnoreCaseAndStaffIdNot(number, in.staffId());
        if (dup) throw bad("Staff number " + number + " is already used.");
        String category = in.category() == null ? "SUPPORT" : in.category().trim().toUpperCase();
        if (!CATEGORIES.contains(category)) throw bad("Choose teaching, support or administration.");
        String method = in.payMethod() == null ? "BANK" : in.payMethod().trim().toUpperCase();
        if (!METHODS.contains(method)) throw bad("Choose how they are paid: bank, M-Pesa, cash or cheque.");
        BigDecimal basic = money(in.basicSalary(), "Basic salary", true);
        if (method.equals("BANK") && trim(in.accountNumber(), 40) == null) throw bad("Enter the bank account number (or choose another way of paying).");
        if (method.equals("MPESA") && trim(in.phone(), 20) == null) throw bad("Enter the M-Pesa phone number.");
        List<StaffPayItem> newItems = new ArrayList<>();
        for (ItemIn i : in.items() == null ? List.<ItemIn>of() : in.items()) newItems.add(item(i));   // check before saving anything

        s.setStaffNumber(number.toUpperCase());
        s.setFullName(name.toUpperCase());
        s.setJobTitle(trim(in.jobTitle(), 60));
        s.setCategory(category);
        s.setTeacherId(in.teacherId());
        s.setPhone(trim(in.phone(), 20));
        s.setKraPin(upper(trim(in.kraPin(), 20)));
        s.setNssfNumber(trim(in.nssfNumber(), 20));
        s.setShaNumber(trim(in.shaNumber(), 20));
        s.setPayMethod(method);
        s.setBankName(trim(in.bankName(), 60));
        s.setBankBranch(trim(in.bankBranch(), 60));
        s.setAccountNumber(trim(in.accountNumber(), 40));
        s.setBankCode(upper(trim(in.bankCode(), 20)));
        s.setBasicSalary(basic);
        s.setStatutory(in.statutory() == null || in.statutory());
        s.setActive(in.active() == null || in.active());
        s.setStartDate(in.startDate());
        s.setNotes(trim(in.notes(), 200));
        if (in.staffId() == null) s.setOnPayroll(true);       // added from Payroll → on the payroll
        staffRepository.save(s);

        if (in.items() != null) {                                // replace the regular items
            itemRepository.deleteAll(itemRepository.findByStaffStaffId(s.getStaffId()));
            itemRepository.flush();
            for (StaffPayItem it : newItems) { it.setStaff(s); itemRepository.save(it); }
        }
        return Map.of("message", s.getFullName() + " saved.", "staffId", s.getStaffId());
    }

    /** What someone would be paid, with the current rates — for the staff form. */
    @Transactional
    public Map<String, Object> preview(PreviewIn in) {
        List<PayrollCalculator.Item> earn = new ArrayList<>(), ded = new ArrayList<>();
        for (ItemIn i : in.items() == null ? List.<ItemIn>of() : in.items()) {
            StaffPayItem it = item(i);
            (it.getKind().equals("EARNING") ? earn : ded).add(new PayrollCalculator.Item(it.getName(), it.getAmount(), it.isTaxable()));
        }
        PayrollCalculator.Result r = PayrollCalculator.compute(money(in.basicSalary(), "Basic salary", true), earn, ded,
                in.statutory() == null || in.statutory(), toRates(currentRatesJson()));
        return resultMap(r);
    }

    // ══ Pay runs ════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public List<Map<String, Object>> runs() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PayrollRun r : runRepository.findAllByOrderByPeriodDesc()) out.add(runMap(r));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> run(Long runId) {
        PayrollRun r = findRun(runId);
        Map<String, Object> m = runMap(r);
        List<Map<String, Object>> slips = new ArrayList<>();
        for (Payslip p : payslipRepository.findByRunRunIdOrderByFullNameAsc(runId)) slips.add(payslipMap(p));
        m.put("payslips", slips);
        m.put("rates", readJson(r.getRatesJson()));
        m.put("treasurerApproval", roleGuard.treasurerApprovalOn());   // only the treasurer may approve
        return m;
    }

    @Transactional
    public Map<String, Object> createRun(String period, String who) {
        String p = period(period);
        if (runRepository.findByPeriod(p).isPresent()) throw bad("There is already a pay run for " + label(p) + ".");
        if (staffRepository.findByActiveTrueOrderByFullNameAsc().stream().noneMatch(StaffMember::isOnPayroll)) throw bad("Add staff members first (Payroll → Staff).");
        PayrollRun r = new PayrollRun();
        r.setPeriod(p);
        r.setStatus("DRAFT");
        r.setRatesJson(currentRatesJson());
        r.setCreatedBy(who);
        r.setCreatedAt(LocalDateTime.now());
        runRepository.save(r);
        buildPayslips(r, Map.of());
        return Map.of("message", "Pay run for " + label(p) + " created as a draft (" + r.getStaffCount() + " staff). Check it, then approve.", "runId", r.getRunId());
    }

    /** Draft only: rebuild every payslip from the current staff records and current rates (keeps this month's adjustments). */
    @Transactional
    public Map<String, Object> recalculate(Long runId) {
        PayrollRun r = draft(runId);
        Map<Long, String> adjustments = new HashMap<>();
        List<Payslip> old = payslipRepository.findByRunRunIdOrderByFullNameAsc(runId);
        old.forEach(p -> { if (p.getAdjustmentsJson() != null) adjustments.put(p.getStaffId(), p.getAdjustmentsJson()); });
        payslipRepository.deleteAll(old);
        payslipRepository.flush();
        r.setRatesJson(currentRatesJson());
        buildPayslips(r, adjustments);
        return Map.of("message", "Recalculated with the current staff details and rates.");
    }

    /** Draft only: this month's one-off earnings/deductions for one person (overtime, an advance…). */
    @Transactional
    public Map<String, Object> setAdjustments(Long payslipId, List<ItemIn> items) {
        Payslip p = payslipRepository.findById(payslipId).orElseThrow(() -> notFound("Payslip not found."));
        PayrollRun r = draft(p.getRun().getRunId());
        List<StaffPayItem> adj = new ArrayList<>();
        for (ItemIn i : items == null ? List.<ItemIn>of() : items) adj.add(item(i));
        StaffMember s = staffRepository.findById(p.getStaffId()).orElseThrow(() -> bad("This staff member no longer exists — press Recalculate."));
        p.setAdjustmentsJson(adj.isEmpty() ? null : toJson(adj.stream().map(PayrollService::itemMap).toList()));
        fill(p, s, itemRepository.findByStaffStaffId(s.getStaffId()), adj, toRates(r.getRatesJson()));
        payslipRepository.save(p);
        totals(r);
        return Map.of("message", p.getFullName() + ": this month's changes saved.");
    }

    @Transactional
    public Map<String, Object> approve(Long runId, String who) {
        PayrollRun r = draft(runId);
        if (roleGuard.treasurerApprovalOn() && !roleGuard.current(jnm.engineer.demo.models.User.Role.TREASURER)) {
            throw bad("Only the treasurer can approve pay runs. The bursar prepares the run; the treasurer approves it.");
        }
        r.setStatus("APPROVED");
        r.setApprovedBy(who);
        r.setApprovedAt(LocalDateTime.now());
        // Approval puts the salaries straight into the books: the month's cost to the school
        // (gross + employer NSSF + employer Housing Levy) becomes an approved expense.
        CurrentTermService.Term term = currentTermService.current();
        jnm.engineer.demo.models.MoneyGroup group = salariesGroup();
        LocalDate today = LocalDate.now();
        Map<String, Object> e = booksService.recordExpense(new FinanceBooksService.ExpenseIn(today, term.yearLabel(), term.term(), group.getGroupId(),
                "Salaries — " + label(r.getPeriod()) + " (" + r.getStaffCount() + " staff, incl. employer NSSF & Housing Levy)",
                "Staff", r.getTotalEmployerCost(), "BANK", "PAYROLL " + r.getPeriod()), who, who);
        r.setExpenseId(((Number) e.get("expenseId")).longValue());
        r.setPaidOn(today);
        r.setPaidBy(who);
        runRepository.save(r);
        return Map.of("message", label(r.getPeriod()) + " approved and recorded as expense " + e.get("voucherNumber")
                + " (" + group.getName() + ", " + term.label() + "). Payslips can now be printed.");
    }

    @Transactional
    public Map<String, Object> reopen(Long runId) {
        PayrollRun r = findRun(runId);
        if (!"APPROVED".equals(r.getStatus())) throw bad("Only an approved (not yet paid) run can be reopened.");
        if (r.getExpenseId() != null) {       // take the salaries back out of the books until it is approved again
            booksService.voidExpense(r.getExpenseId(), "Pay run " + label(r.getPeriod()) + " reopened for changes", r.getApprovedBy());
            r.setExpenseId(null);
            r.setPaidOn(null);
            r.setPaidBy(null);
        }
        r.setStatus("DRAFT");
        runRepository.save(r);
        return Map.of("message", label(r.getPeriod()) + " is a draft again. Its expense was voided; approving again records it afresh.");
    }

    /** Approved → paid: records the month's total cost (gross + employer NSSF + employer Housing Levy) as an expense. */
    @Transactional
    public Map<String, Object> markPaid(Long runId, PayIn in, String who) {
        PayrollRun r = findRun(runId);
        if (!"APPROVED".equals(r.getStatus())) throw bad("Approve the pay run before marking it as paid.");
        if (r.getExpenseId() != null) throw bad(label(r.getPeriod()) + " was recorded as an expense when it was approved.");
        if (in == null || in.paidOn() == null) throw bad("Enter the date it was paid.");
        String ref = in.reference() == null || in.reference().isBlank() ? "PAYROLL " + r.getPeriod() : in.reference().trim();
        Map<String, Object> e = booksService.recordExpense(new FinanceBooksService.ExpenseIn(in.paidOn(), in.yearLabel(), in.term(), in.groupId(),
                "Salaries — " + label(r.getPeriod()) + " (" + r.getStaffCount() + " staff, incl. employer NSSF & Housing Levy)",
                "Staff", r.getTotalEmployerCost(), in.method(), ref), who,
                r.getApprovedBy() != null ? r.getApprovedBy() : who);   // the run was already approved — no second approval
        r.setStatus("PAID");
        r.setPaidOn(in.paidOn());
        r.setPaidBy(who);
        r.setExpenseId(((Number) e.get("expenseId")).longValue());
        runRepository.save(r);
        return Map.of("message", label(r.getPeriod()) + " marked as paid and recorded as expense " + e.get("voucherNumber") + ".");
    }

    @Transactional
    public Map<String, Object> deleteRun(Long runId) {
        PayrollRun r = draft(runId);
        payslipRepository.deleteAll(payslipRepository.findByRunRunIdOrderByFullNameAsc(runId));
        runRepository.delete(r);
        return Map.of("message", "Draft pay run for " + label(r.getPeriod()) + " deleted.");
    }

    // ══ Bank salary upload file (the bank's Excel template) ═════════════════
    public record BankFile(String fileName, byte[] bytes) {}
    static final List<String> BANK_HEADER = List.of("Transaction Type Code", "Debit Account No.", "Beneficiary Account No.", "Beneficiary Name",
            "Beneficiary Bank Swift Code / Local Bank Code", "Purpose Code", "Transaction Currency", "Payment Amount", "Payment Type",
            "Service Provider", "Beneficiary Mobile No", "Remarks");
    private static final double[] BANK_WIDTHS = {20.3, 16.3, 21.4, 33.1, 26.3, 12.7, 18.9, 15.6, 15.4, 16.6, 21.6, 10.7};

    /** The school's bank settings: saved with the rates (Payroll → Rates), else the starting values. */
    private JsonNode bankSettings() {
        try {
            JsonNode n = JSON.readTree(currentRatesJson()).get("bankFile");
            if (n != null) return n;
            JsonNode seed = JSON.readTree(seedRatesJson()).get("bankFile");
            return seed != null ? seed : JSON.readTree("{}");
        } catch (Exception e) { throw bad("The bank settings are not valid."); }
    }
    private static String txt(JsonNode n, String f, String dflt) { return n != null && n.hasNonNull(f) && !n.get(f).asText().isBlank() ? n.get(f).asText().trim() : dflt; }

    /** Check before downloading: who goes in the file, and anything that needs fixing first. */
    @Transactional(readOnly = true)
    public Map<String, Object> bankFileCheck(Long runId) {
        PayrollRun r = findRun(runId);
        JsonNode b = bankSettings();
        List<Payslip> slips = payslipRepository.findByRunRunIdOrderByFullNameAsc(runId);
        List<String> missing = new ArrayList<>(), others = new ArrayList<>();
        int rows = 0; BigDecimal total = BigDecimal.ZERO;
        for (Payslip p : slips) {
            if (!"BANK".equals(p.getPayMethod())) { others.add(p.getFullName() + " (" + p.getPayMethod() + ", " + p.getNet() + ")"); continue; }
            String problem = bankProblem(p, b);
            if (problem != null) { missing.add(p.getFullName() + ": " + problem); continue; }   // left out of the file AND the total
            rows++; total = total.add(p.getNet());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus());
        m.put("debitAccount", txt(b, "debitAccount", ""));
        m.put("rows", rows);
        m.put("total", total);
        m.put("missing", missing);
        m.put("notInFile", others);
        return m;
    }

    @Transactional(readOnly = true)
    public BankFile bankFile(Long runId) {
        PayrollRun r = findRun(runId);
        if ("DRAFT".equals(r.getStatus())) throw bad("Approve the pay run first — the bank file is made from the approved figures.");
        JsonNode b = bankSettings();
        String debit = txt(b, "debitAccount", null);
        if (debit == null) throw bad("Enter the school's bank account number first (Payroll → Rates → Bank upload file).");
        List<List<String>> rows = new ArrayList<>();
        rows.add(BANK_HEADER);
        for (Payslip p : payslipRepository.findByRunRunIdOrderByFullNameAsc(runId)) {
            if (!"BANK".equals(p.getPayMethod()) || p.getNet() == null || p.getNet().signum() <= 0) continue;
            if (bankProblem(p, b) != null) continue;   // left out — listed by the check
            boolean same = sameBank(p, b);
            rows.add(Arrays.asList(same ? "BT" : "LBT", debit, p.getAccountNumber(), p.getFullName(),
                    p.getBankCode() != null ? p.getBankCode() : "", txt(b, "purposeCode", "SAL"), txt(b, "currency", "KES"),
                    p.getNet().setScale(2, RoundingMode.HALF_UP).toPlainString(), same ? "" : txt(b, "otherBankPaymentType", "EFT"),
                    "", "", txt(b, "remarks", "SAL " + r.getPeriod())));
        }
        if (rows.size() == 1) throw bad("Nobody in this pay run is paid by bank.");
        try {
            return new BankFile("Salaries " + label(r.getPeriod()) + ".xlsx", XlsxWriter.write("Sheet1", rows, BANK_WIDTHS));
        } catch (java.io.IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not make the file."); }
    }

    /** Why someone can't go in the bank file yet (null = fine). Same rule for the check and the file. */
    private static String bankProblem(Payslip p, JsonNode b) {
        if (p.getAccountNumber() == null || p.getAccountNumber().isBlank()) return "no account number";
        if (!sameBank(p, b) && (p.getBankCode() == null || p.getBankCode().isBlank()))
            return "no bank code (" + (p.getBankName() == null || p.getBankName().isBlank() ? "bank not set" : p.getBankName()) + ")";
        return null;
    }

    /** Same bank as the school (BT)? By bank code when both are set, otherwise by bank name. */
    private static boolean sameBank(Payslip p, JsonNode b) {
        String code = txt(b, "schoolBankCode", ""), name = txt(b, "schoolBankName", "");
        if (!code.isEmpty() && p.getBankCode() != null && !p.getBankCode().isBlank()) return code.equalsIgnoreCase(p.getBankCode().trim());
        return !name.isEmpty() && p.getBankName() != null && p.getBankName().toLowerCase().contains(name.toLowerCase());
    }

    // ══ helpers ═════════════════════════════════════════════════════════════
    /** The money group salaries are booked to: an existing Salaries/Wages/Payroll/Staff group, else one is created (last in priority). */
    private jnm.engineer.demo.models.MoneyGroup salariesGroup() {
        List<jnm.engineer.demo.models.MoneyGroup> all = groupRepository.findAllByOrderByPriorityAscNameAsc();
        for (String word : List.of("salar", "wage", "payroll", "staff")) {
            for (jnm.engineer.demo.models.MoneyGroup g : all) {
                if (g.isActive() && g.getName() != null && g.getName().toLowerCase().contains(word)) return g;
            }
        }
        jnm.engineer.demo.models.MoneyGroup g = new jnm.engineer.demo.models.MoneyGroup();
        g.setName("Salaries & Wages");
        g.setColor("#6f42c1");
        g.setActive(true);
        g.setPriority(all.stream().map(x -> x.getPriority() == null ? 0 : x.getPriority()).max(Integer::compare).orElse(0) + 1);
        return groupRepository.save(g);
    }

    private void buildPayslips(PayrollRun r, Map<Long, String> adjustments) {
        PayrollCalculator.Rates rates = toRates(r.getRatesJson());
        for (StaffMember s : staffRepository.findByActiveTrueOrderByFullNameAsc()) {
            if (!s.isOnPayroll()) continue;                       // e.g. TSC teachers
            Payslip p = new Payslip();
            p.setRun(r);
            List<StaffPayItem> adj = new ArrayList<>();
            String a = adjustments.get(s.getStaffId());
            if (a != null) {
                p.setAdjustmentsJson(a);
                for (Map<String, Object> m : readList(a)) {
                    adj.add(item(new ItemIn((String) m.get("kind"), (String) m.get("name"),
                            new BigDecimal(String.valueOf(m.get("amount"))), (Boolean) m.get("taxable"))));
                }
            }
            fill(p, s, itemRepository.findByStaffStaffId(s.getStaffId()), adj, rates);
            payslipRepository.save(p);
        }
        totals(r);
    }

    /** Copy the person's details onto the payslip and work out the pay. */
    private static void fill(Payslip p, StaffMember s, List<StaffPayItem> regular, List<StaffPayItem> adj, PayrollCalculator.Rates rates) {
        p.setStaffId(s.getStaffId());
        p.setStaffNumber(s.getStaffNumber());
        p.setFullName(s.getFullName());
        p.setJobTitle(s.getJobTitle());
        p.setCategory(s.getCategory());
        p.setKraPin(s.getKraPin());
        p.setNssfNumber(s.getNssfNumber());
        p.setShaNumber(s.getShaNumber());
        p.setPayMethod(s.getPayMethod());
        p.setBankName(s.getBankName());
        p.setAccountNumber(s.getAccountNumber());
        p.setBankCode(s.getBankCode());
        p.setPhone(s.getPhone());
        List<PayrollCalculator.Item> earn = new ArrayList<>(), ded = new ArrayList<>();
        List<Map<String, Object>> shown = new ArrayList<>();
        for (int listNo = 0; listNo < 2; listNo++) {
            for (StaffPayItem i : listNo == 0 ? regular : adj) {
                (i.getKind().equals("EARNING") ? earn : ded).add(new PayrollCalculator.Item(i.getName(), i.getAmount(), i.isTaxable()));
                Map<String, Object> m = itemMap(i);
                m.put("oneOff", listNo == 1);
                shown.add(m);
            }
        }
        PayrollCalculator.Result r = PayrollCalculator.compute(s.getBasicSalary(), earn, ded, s.isStatutory(), rates);
        p.setBasic(r.basic());
        p.setGross(r.gross());
        p.setTaxablePay(r.taxablePay());
        p.setNssf(r.nssf());
        p.setShif(r.shif());
        p.setHousingLevy(r.housingLevy());
        p.setPayeBeforeRelief(r.payeBeforeRelief());
        p.setRelief(r.relief());
        p.setPaye(r.paye());
        p.setOtherDeductions(r.otherDeductions());
        p.setNet(r.net());
        p.setEmployerNssf(r.employerNssf());
        p.setEmployerHousing(r.employerHousingLevy());
        p.setEmployerCost(r.employerCost());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("items", shown);
        detail.put("payeWorkings", r.bandLines());
        detail.put("statutory", s.isStatutory());
        p.setDetailJson(toJson(detail));
    }

    private void totals(PayrollRun r) {
        List<Payslip> slips = payslipRepository.findByRunRunIdOrderByFullNameAsc(r.getRunId());
        r.setStaffCount(slips.size());
        r.setTotalGross(sum(slips, Payslip::getGross));
        r.setTotalNet(sum(slips, Payslip::getNet));
        r.setTotalPaye(sum(slips, Payslip::getPaye));
        r.setTotalNssf(sum(slips, Payslip::getNssf));
        r.setTotalShif(sum(slips, Payslip::getShif));
        r.setTotalHousing(sum(slips, Payslip::getHousingLevy));
        r.setTotalOtherDeductions(sum(slips, Payslip::getOtherDeductions));
        r.setTotalEmployerNssf(sum(slips, Payslip::getEmployerNssf));
        r.setTotalEmployerHousing(sum(slips, Payslip::getEmployerHousing));
        r.setTotalEmployerCost(sum(slips, Payslip::getEmployerCost));
        runRepository.save(r);
    }

    private static BigDecimal sum(List<Payslip> l, java.util.function.Function<Payslip, BigDecimal> f) {
        return l.stream().map(f).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static StaffPayItem item(ItemIn i) {
        String kind = i.kind() == null ? "" : i.kind().trim().toUpperCase();
        if (!kind.equals("EARNING") && !kind.equals("DEDUCTION")) throw bad("Each item must be an allowance or a deduction.");
        String name = trim(i.name(), 60);
        if (name == null) throw bad("Every allowance and deduction needs a name.");
        StaffPayItem it = new StaffPayItem();
        it.setKind(kind);
        it.setName(name);
        it.setAmount(money(i.amount(), name, false));
        it.setTaxable(kind.equals("EARNING") && (i.taxable() == null || i.taxable()));
        return it;
    }

    private static Map<String, Object> itemMap(StaffPayItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", i.getKind());
        m.put("name", i.getName());
        m.put("amount", i.getAmount());
        m.put("taxable", i.isTaxable());
        return m;
    }

    private static Map<String, Object> staffMap(StaffMember s, List<StaffPayItem> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("staffId", s.getStaffId());
        m.put("staffNumber", s.getStaffNumber());
        m.put("fullName", s.getFullName());
        m.put("jobTitle", s.getJobTitle());
        m.put("category", s.getCategory());
        m.put("teacherId", s.getTeacherId());
        m.put("phone", s.getPhone());
        m.put("kraPin", s.getKraPin());
        m.put("nssfNumber", s.getNssfNumber());
        m.put("shaNumber", s.getShaNumber());
        m.put("payMethod", s.getPayMethod());
        m.put("bankName", s.getBankName());
        m.put("bankBranch", s.getBankBranch());
        m.put("accountNumber", s.getAccountNumber());
        m.put("bankCode", s.getBankCode());
        m.put("basicSalary", s.getBasicSalary());
        m.put("statutory", s.isStatutory());
        m.put("active", s.isActive());
        m.put("startDate", s.getStartDate() != null ? s.getStartDate().toString() : null);
        m.put("notes", s.getNotes());
        m.put("items", items.stream().map(PayrollService::itemMap).toList());
        return m;
    }

    private static Map<String, Object> resultMap(PayrollCalculator.Result r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("basic", r.basic());
        m.put("gross", r.gross());
        m.put("taxableEarnings", r.taxableEarnings());
        m.put("nssf", r.nssf());
        m.put("shif", r.shif());
        m.put("housingLevy", r.housingLevy());
        m.put("taxablePay", r.taxablePay());
        m.put("payeBeforeRelief", r.payeBeforeRelief());
        m.put("relief", r.relief());
        m.put("paye", r.paye());
        m.put("otherDeductions", r.otherDeductions());
        m.put("totalDeductions", r.totalDeductions());
        m.put("net", r.net());
        m.put("employerNssf", r.employerNssf());
        m.put("employerHousingLevy", r.employerHousingLevy());
        m.put("employerCost", r.employerCost());
        m.put("payeWorkings", r.bandLines());
        return m;
    }

    private static Map<String, Object> runMap(PayrollRun r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", r.getRunId());
        m.put("period", r.getPeriod());
        m.put("label", label(r.getPeriod()));
        m.put("status", r.getStatus());
        m.put("staffCount", r.getStaffCount());
        m.put("totalGross", r.getTotalGross());
        m.put("totalNet", r.getTotalNet());
        m.put("totalPaye", r.getTotalPaye());
        m.put("totalNssf", r.getTotalNssf());
        m.put("totalShif", r.getTotalShif());
        m.put("totalHousing", r.getTotalHousing());
        m.put("totalOtherDeductions", r.getTotalOtherDeductions());
        m.put("totalEmployerNssf", r.getTotalEmployerNssf());
        m.put("totalEmployerHousing", r.getTotalEmployerHousing());
        m.put("totalEmployerCost", r.getTotalEmployerCost());
        m.put("createdBy", r.getCreatedBy());
        m.put("approvedBy", r.getApprovedBy());
        m.put("paidOn", r.getPaidOn() != null ? r.getPaidOn().toString() : null);
        m.put("expenseId", r.getExpenseId());
        return m;
    }

    private static Map<String, Object> payslipMap(Payslip p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("payslipId", p.getPayslipId());
        m.put("staffId", p.getStaffId());
        m.put("staffNumber", p.getStaffNumber());
        m.put("fullName", p.getFullName());
        m.put("jobTitle", p.getJobTitle());
        m.put("category", p.getCategory());
        m.put("kraPin", p.getKraPin());
        m.put("nssfNumber", p.getNssfNumber());
        m.put("shaNumber", p.getShaNumber());
        m.put("payMethod", p.getPayMethod());
        m.put("bankName", p.getBankName());
        m.put("accountNumber", p.getAccountNumber());
        m.put("bankCode", p.getBankCode());
        m.put("phone", p.getPhone());
        m.put("basic", p.getBasic());
        m.put("gross", p.getGross());
        m.put("taxablePay", p.getTaxablePay());
        m.put("nssf", p.getNssf());
        m.put("shif", p.getShif());
        m.put("housingLevy", p.getHousingLevy());
        m.put("payeBeforeRelief", p.getPayeBeforeRelief());
        m.put("relief", p.getRelief());
        m.put("paye", p.getPaye());
        m.put("otherDeductions", p.getOtherDeductions());
        m.put("net", p.getNet());
        m.put("employerNssf", p.getEmployerNssf());
        m.put("employerHousing", p.getEmployerHousing());
        m.put("employerCost", p.getEmployerCost());
        m.put("detail", p.getDetailJson() == null ? Map.of() : readJson(p.getDetailJson()));
        m.put("adjustments", p.getAdjustmentsJson() == null ? List.of() : readList(p.getAdjustmentsJson()));
        return m;
    }

    private PayrollRun findRun(Long id) { return runRepository.findById(id).orElseThrow(() -> notFound("Pay run not found.")); }
    private PayrollRun draft(Long id) {
        PayrollRun r = findRun(id);
        if (!"DRAFT".equals(r.getStatus())) throw bad("This pay run is " + r.getStatus().toLowerCase() + " — only a draft can be changed.");
        return r;
    }

    static String label(String period) {
        YearMonth ym = YearMonth.parse(period);
        return ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear();
    }
    private static String period(String p) {
        try { return YearMonth.parse(String.valueOf(p).trim()).toString(); }
        catch (Exception e) { throw bad("Choose the month, e.g. 2026-09."); }
    }
    private static BigDecimal money(BigDecimal v, String label, boolean zeroOk) {
        if (v == null) { if (zeroOk) return BigDecimal.ZERO.setScale(2); throw bad("Enter the amount for " + label + "."); }
        if (v.signum() < 0 || (!zeroOk && v.signum() == 0)) throw bad(label + " must be more than 0.");
        if (v.stripTrailingZeros().scale() > 2) throw bad(label + " can have at most 2 decimal places.");
        if (v.compareTo(MAX) > 0) throw bad(label + " looks too large — please check it.");
        return v.setScale(2, RoundingMode.HALF_UP);
    }
    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
    private static String upper(String s) { return s == null ? null : s.toUpperCase(); }
    private static String toJson(Object o) {
        try { return JSON.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static Map<String, Object> readJson(String s) {
        try { return JSON.readValue(s, new TypeReference<Map<String, Object>>() {}); } catch (Exception e) { return new LinkedHashMap<>(); }
    }
    private static List<Map<String, Object>> readList(String s) {
        try { return JSON.readValue(s, new TypeReference<List<Map<String, Object>>>() {}); } catch (Exception e) { return new ArrayList<>(); }
    }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
}
