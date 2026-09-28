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

    @PostMapping("/expenses/{id}/void")
    public ResponseEntity<Map<String, Object>> voidExpense(@PathVariable Long id, @RequestBody VoidIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(booksService.voidExpense(id, in == null ? null : in.reason(), me != null ? me.getUsername() : null));
    }

    // ── report ──
    @GetMapping("/reports/groups")
    public ResponseEntity<Map<String, Object>> groupReport(@RequestParam String yearLabel, @RequestParam Integer term) {
        return ResponseEntity.ok(booksService.groupReport(yearLabel, term));
    }
}
