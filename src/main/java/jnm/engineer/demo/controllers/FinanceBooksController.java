package jnm.engineer.demo.controllers;

import jnm.engineer.demo.services.FinanceBooksService;
import jnm.engineer.demo.services.MoneyGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Money groups, expenses and the money-in / money-out report.
 * Under /api/finance, so SecurityConfig limits it to ADMIN and ACCOUNTANT.
 */
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceBooksController {
    private final MoneyGroupService moneyGroupService;
    private final FinanceBooksService booksService;
    private final jnm.engineer.demo.services.FinanceService financeService;

    public record VoidIn(String reason) {}

    // ── money groups ──
    @GetMapping("/groups")
    public ResponseEntity<Map<String, Object>> groups() { return ResponseEntity.ok(moneyGroupService.overview()); }

    @PutMapping("/groups")
    public ResponseEntity<Map<String, Object>> saveGroups(@RequestBody List<MoneyGroupService.GroupIn> in) {
        return ResponseEntity.ok(moneyGroupService.saveGroups(in));
    }

    @PutMapping("/groups/items")
    public ResponseEntity<Map<String, Object>> saveItems(@RequestBody List<MoneyGroupService.ItemIn> in) {
        return ResponseEntity.ok(moneyGroupService.saveItems(in));
    }

    @PutMapping("/groups/charges")
    public ResponseEntity<Map<String, Object>> saveChargeGroups(@RequestBody List<MoneyGroupService.ChargeGroupIn> in) {
        return ResponseEntity.ok(moneyGroupService.saveChargeGroups(in));
    }

    // ── expenses ──
    @GetMapping("/expenses")
    public ResponseEntity<Map<String, Object>> expenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String yearLabel, @RequestParam(required = false) Integer term) {
        return ResponseEntity.ok(yearLabel != null ? booksService.expensesForTerm(yearLabel, term) : booksService.expenses(from, to));
    }

    @PostMapping("/expenses")
    public ResponseEntity<Map<String, Object>> record(@RequestBody FinanceBooksService.ExpenseIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(booksService.recordExpense(in, me != null ? me.getUsername() : null));
    }

    // ── Bills: approve many, the list to pay, cheques ──
    public record IdsIn(List<Long> ids) {}

    /** TREASURER only (SecurityConfig). */
    @PostMapping("/expenses/approve")
    public ResponseEntity<Map<String, Object>> approveMany(@RequestBody IdsIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(booksService.approveMany(in == null ? null : in.ids(), me != null ? me.getUsername() : null));
    }

    @GetMapping("/expenses/to-pay")
    public ResponseEntity<Map<String, Object>> toPay() { return ResponseEntity.ok(booksService.toPay()); }

    @GetMapping("/cheques")
    public ResponseEntity<Map<String, Object>> cheques(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(booksService.cheques(from, to));
    }

    /** TREASURER only (SecurityConfig). */
    @PostMapping("/cheques")
    public ResponseEntity<Map<String, Object>> raiseCheque(@RequestBody FinanceBooksService.ChequeIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(booksService.raiseCheque(in, me != null ? me.getUsername() : null));
    }

    // ── M-Pesa float: cheque to top up the school's M-Pesa line, adjustments, paying bills by M-Pesa ──
    /** TREASURER only (SecurityConfig). */
    @PostMapping("/cheques/float")
    public ResponseEntity<Map<String, Object>> floatCheque(@RequestBody FinanceBooksService.FloatChequeIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(booksService.raiseFloatCheque(in, me != null ? me.getUsername() : null));
    }

    /** TREASURER only (SecurityConfig). */
    @PostMapping("/float/adjust")
    public ResponseEntity<Map<String, Object>> adjustFloat(@RequestBody FinanceBooksService.FloatAdjustIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(booksService.adjustFloat(in, me != null ? me.getUsername() : null));
    }

    /** Pay approved bills from the M-Pesa line (finance staff). */
    @PostMapping("/expenses/pay-mpesa")
    public ResponseEntity<Map<String, Object>> payByMpesa(@RequestBody FinanceBooksService.MpesaPayIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(booksService.payByMpesa(in, me != null ? me.getUsername() : null));
    }

    @GetMapping("/float")
    public ResponseEntity<Map<String, Object>> floatStatement(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(booksService.floatStatement(from, to));
    }

    /** TREASURER only (SecurityConfig). */
    @PostMapping("/cheques/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelCheque(@PathVariable Long id, @RequestBody RejectIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(booksService.cancelCheque(id, in == null ? null : in.reason(), me != null ? me.getUsername() : null));
    }

    // ── Treasurer approval of bills ──
    @GetMapping("/expenses/pending")
    public ResponseEntity<Map<String, Object>> pendingExpenses() { return ResponseEntity.ok(booksService.pendingExpenses()); }

    /** TREASURER only (SecurityConfig). */
    @PostMapping("/expenses/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveExpense(@PathVariable Long id, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(booksService.approveExpense(id, (me != null ? me.getUsername() : null)));
    }

    public record RejectIn(String reason) {}

    /** TREASURER only (SecurityConfig). */
    @PostMapping("/expenses/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectExpense(@PathVariable Long id, @RequestBody RejectIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(booksService.rejectExpense(id, in == null ? null : in.reason(), (me != null ? me.getUsername() : null)));
    }

    @PostMapping("/expenses/{id}/void")
    public ResponseEntity<Map<String, Object>> voidExpense(@PathVariable Long id, @RequestBody VoidIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(booksService.voidExpense(id, in == null ? null : in.reason(), me != null ? me.getUsername() : null));
    }

    // ── report ──
    // ── Bank reconciliation: everything recorded under the statement's codes, whatever its date ──
    public record CodesIn(List<String> codes) {}

    @PostMapping("/reconcile/lookup")
    public ResponseEntity<Map<String, Object>> reconcileLookup(@RequestBody CodesIn in) {
        List<String> codes = in == null || in.codes() == null ? List.of() : in.codes();
        return ResponseEntity.ok(Map.of("payments", financeService.paymentsByCodes(codes), "expenses", booksService.expensesByCodes(codes)));
    }

    // ── Bank reconciliation: confirm matched records in the books ──
    @PostMapping("/bank-confirm")
    public ResponseEntity<Map<String, Object>> bankConfirm(@RequestBody FinanceBooksService.BankConfirmIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(booksService.confirmInBank(in, me != null ? me.getUsername() : null));
    }

    @PostMapping("/bank-confirm/undo")
    public ResponseEntity<Map<String, Object>> bankUndo(@RequestBody FinanceBooksService.BankUndoIn in) {
        return ResponseEntity.ok(booksService.undoBankConfirm(in));
    }

    @GetMapping("/reports/groups")
    public ResponseEntity<Map<String, Object>> groupReport(@RequestParam String yearLabel, @RequestParam Integer term) {
        return ResponseEntity.ok(booksService.groupReport(yearLabel, term));
    }
}
