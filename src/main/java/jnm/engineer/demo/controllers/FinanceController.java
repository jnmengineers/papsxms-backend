package jnm.engineer.demo.controllers;

import jnm.engineer.demo.services.FinanceService;
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
 * Fees — web layer only. All rules and calculations are in FinanceService.
 * Only ADMIN and ACCOUNTANT (bursar) may call these — see SecurityConfig.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceService financeService;

    private static String who(UserDetails me) { return me != null ? me.getUsername() : null; }

    // ── Fee structures ──
    @GetMapping("/structures")
    public ResponseEntity<List<Map<String, Object>>> structures(@RequestParam(required = false) String yearLabel,
                                                                @RequestParam(required = false) Integer term) {
        return ResponseEntity.ok(financeService.listStructures(yearLabel, term));
    }

    @PutMapping("/structures")
    public ResponseEntity<Map<String, Object>> saveStructure(@RequestBody FinanceService.StructureIn in) {
        return ResponseEntity.ok(financeService.saveStructure(in));
    }

    @DeleteMapping("/structures/{id}")
    public ResponseEntity<Map<String, String>> deleteStructure(@PathVariable Long id) {
        financeService.deleteStructure(id);
        return ResponseEntity.ok(Map.of("message", "Fee structure deleted. Invoices already issued are not affected."));
    }

    // ── Billing ──
    @GetMapping("/billing/preview")
    public ResponseEntity<Map<String, Object>> billingPreview(@RequestParam String yearLabel, @RequestParam Integer term) {
        return ResponseEntity.ok(financeService.previewBilling(yearLabel, term));
    }

    @PostMapping("/billing/run")
    public ResponseEntity<Map<String, Object>> runBilling(@RequestParam String yearLabel, @RequestParam Integer term,
                                                          @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(financeService.runBilling(yearLabel, term, who(me)));
    }

    // ── Balances & statements ──
    @GetMapping("/balances")
    public ResponseEntity<Map<String, Object>> balances(@RequestParam(required = false) Long classId) {
        return ResponseEntity.ok(financeService.balances(classId));
    }

    @GetMapping("/students/{studentId}/statement")
    public ResponseEntity<Map<String, Object>> statement(@PathVariable Long studentId) {
        return ResponseEntity.ok(financeService.statement(studentId));
    }

    // ── Payments ──
    @PostMapping("/payments")
    public ResponseEntity<Map<String, Object>> recordPayment(@RequestBody FinanceService.PaymentIn in,
                                                             @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(financeService.recordPayment(in, who(me)));
    }

    @PostMapping("/payments/{id}/reverse")
    public ResponseEntity<Map<String, Object>> reverse(@PathVariable Long id, @RequestBody FinanceService.ReverseIn in,
                                                       @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(financeService.reversePayment(id, in == null ? null : in.reason(), who(me)));
    }

    @GetMapping("/payments")
    public ResponseEntity<Map<String, Object>> payments(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                        @RequestParam(required = false) Long classId) {
        return ResponseEntity.ok(financeService.payments(from, to, classId));
    }

    /** Balance slips / class balance list for one term: by class, by section, or everyone. */
    @GetMapping("/class-sheet")
    public ResponseEntity<Map<String, Object>> classSheet(@RequestParam(required = false) Long classId,
                                                          @RequestParam(required = false) String section,
                                                          @RequestParam String yearLabel, @RequestParam Integer term) {
        return ResponseEntity.ok(financeService.classSheet(classId, section, yearLabel, term));
    }
}
