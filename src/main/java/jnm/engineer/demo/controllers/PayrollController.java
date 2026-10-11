package jnm.engineer.demo.controllers;

import jnm.engineer.demo.services.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Payroll — ADMIN and ACCOUNTANT only (SecurityConfig), including reading. */
@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollController {
    private final PayrollService service;

    public record PeriodIn(String period) {}

    private static String who(UserDetails me) { return me != null ? me.getUsername() : null; }

    // ── rates ──
    @GetMapping("/rates")
    public ResponseEntity<Map<String, Object>> rates() { return ResponseEntity.ok(service.rates()); }

    @PutMapping("/rates")
    public ResponseEntity<Map<String, Object>> saveRates(@RequestBody Map<String, Object> in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.saveRates(in, who(me)));
    }

    // ── staff ──
    @GetMapping("/staff")
    public ResponseEntity<List<Map<String, Object>>> staff() { return ResponseEntity.ok(service.staff()); }

    @PutMapping("/staff")
    public ResponseEntity<Map<String, Object>> saveStaff(@RequestBody PayrollService.StaffIn in) { return ResponseEntity.ok(service.saveStaff(in)); }

    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(@RequestBody PayrollService.PreviewIn in) { return ResponseEntity.ok(service.preview(in)); }

    // ── pay runs ──
    @GetMapping("/runs")
    public ResponseEntity<List<Map<String, Object>>> runs() { return ResponseEntity.ok(service.runs()); }

    @GetMapping("/runs/{id}")
    public ResponseEntity<Map<String, Object>> run(@PathVariable Long id) { return ResponseEntity.ok(service.run(id)); }

    @PostMapping("/runs")
    public ResponseEntity<Map<String, Object>> create(@RequestBody PeriodIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.createRun(in == null ? null : in.period(), who(me)));
    }

    @PostMapping("/runs/{id}/recalculate")
    public ResponseEntity<Map<String, Object>> recalc(@PathVariable Long id) { return ResponseEntity.ok(service.recalculate(id)); }

    @PutMapping("/payslips/{id}/adjustments")
    public ResponseEntity<Map<String, Object>> adjust(@PathVariable Long id, @RequestBody List<PayrollService.ItemIn> items) {
        return ResponseEntity.ok(service.setAdjustments(id, items));
    }

    @PostMapping("/runs/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id, @AuthenticationPrincipal UserDetails me) { return ResponseEntity.ok(service.approve(id, who(me))); }

    @PostMapping("/runs/{id}/reopen")
    public ResponseEntity<Map<String, Object>> reopen(@PathVariable Long id) { return ResponseEntity.ok(service.reopen(id)); }

    @PostMapping("/runs/{id}/paid")
    public ResponseEntity<Map<String, Object>> paid(@PathVariable Long id, @RequestBody PayrollService.PayIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.markPaid(id, in, who(me)));
    }

    /** Who goes in the bank file and what needs fixing first. */
    @GetMapping("/runs/{id}/bank-file/check")
    public ResponseEntity<Map<String, Object>> bankFileCheck(@PathVariable Long id) { return ResponseEntity.ok(service.bankFileCheck(id)); }

    /** The bank's salary upload file (Excel), in the bank's template layout. */
    @GetMapping("/runs/{id}/bank-file")
    public ResponseEntity<byte[]> bankFile(@PathVariable Long id) {
        PayrollService.BankFile f = service.bankFile(id);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.fileName() + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(f.bytes());
    }

    @DeleteMapping("/runs/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) { return ResponseEntity.ok(service.deleteRun(id)); }
}
