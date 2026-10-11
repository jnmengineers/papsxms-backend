package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One month's payroll. DRAFT → APPROVED (locked) → PAID (recorded as an expense). */
@Entity
@Table(name = "payroll_runs")
@Getter
@Setter
@NoArgsConstructor
public class PayrollRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long runId;

    @Column(nullable = false, unique = true, length = 7)
    private String period;                                   // "2026-09"

    @Column(nullable = false, length = 10)
    private String status = "DRAFT";                         // DRAFT, APPROVED, PAID

    /** The rates used for this run (kept, so old payslips never change when rates are updated). */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String ratesJson;

    private Integer staffCount;
    @Column(precision = 14, scale = 2) private BigDecimal totalGross;
    @Column(precision = 14, scale = 2) private BigDecimal totalNet;
    @Column(precision = 14, scale = 2) private BigDecimal totalPaye;
    @Column(precision = 14, scale = 2) private BigDecimal totalNssf;
    @Column(precision = 14, scale = 2) private BigDecimal totalShif;
    @Column(precision = 14, scale = 2) private BigDecimal totalHousing;
    @Column(precision = 14, scale = 2) private BigDecimal totalOtherDeductions;
    @Column(precision = 14, scale = 2) private BigDecimal totalEmployerNssf;
    @Column(precision = 14, scale = 2) private BigDecimal totalEmployerHousing;
    @Column(precision = 14, scale = 2) private BigDecimal totalEmployerCost;

    @Column(length = 50) private String createdBy;
    private LocalDateTime createdAt;
    @Column(length = 50) private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDate paidOn;
    @Column(length = 50) private String paidBy;
    private Long expenseId;                                  // the expense recorded when paid
    @Column(length = 200) private String notes;
}
