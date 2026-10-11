package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Money the school spent, in a money group and a term (e.g. "Maize flour, 5 bags" — Meals — 2026 T3).
 * Never deleted: a mistake is VOIDED with a reason, so the books always show what happened.
 */
@Entity
@Table(name = "expenses",
        indexes = {@Index(name = "ix_expense_term", columnList = "year_label, term"),
                   @Index(name = "ix_expense_date", columnList = "spent_on")})
@Getter
@Setter
@NoArgsConstructor
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long expenseId;

    @Column(length = 30)
    private String voucherNumber;              // PV-2026-00012 (set right after saving)

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Column(name = "year_label", nullable = false, length = 10)
    private String yearLabel;

    @Column(nullable = false)
    private Integer term;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private MoneyGroup group;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(length = 100)
    private String payee;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String method;                     // CASH, MPESA, BANK, CHEQUE

    @Column(length = 60)
    private String reference;                  // M-Pesa code, cheque no., supplier receipt no.

    @Column(length = 50) private String recordedBy;
    @Column(nullable = false) private LocalDateTime recordedAt;

    /**
     * Treasurer approval: APPROVED, PENDING (awaiting the treasurer) or REJECTED.
     * Empty = APPROVED (bills recorded before approval existed). Only approved bills count in the books.
     */
    @Column(length = 10) private String approvalStatus;
    @Column(length = 50) private String approvedBy;
    private LocalDateTime approvedAt;
    @Column(length = 200) private String rejectReason;

    /**
     * Paid yet? false = approved bill waiting for the treasurer's cheque. Empty = paid
     * (everything recorded before bills and cheques existed was recorded as already paid).
     */
    @Column private Boolean paid;
    private LocalDate paidOn;
    private Long chequeId;                      // the cheque that paid it

    /** Confirmed on a bank statement (Finance → Bank Reconciliation). Empty = not confirmed yet. */
    private LocalDate bankConfirmedOn;     // the date on the bank statement
    @Column(length = 200) private String bankLine;    // the statement line it was matched to
    @Column(length = 50) private String bankConfirmedBy;
    private LocalDateTime bankConfirmedAt;
    /** Paid from the school's M-Pesa line (the float): lowers the float balance. */
    @Column private Boolean fromFloat;

    public boolean isPaidOut() { return !Boolean.FALSE.equals(paid); }

    /** Counts in totals and reports: approved, PAID and not voided. */
    public boolean counts() { return !voided && (approvalStatus == null || "APPROVED".equals(approvalStatus)) && isPaidOut(); }

    @Column(nullable = false) private boolean voided = false;
    @Column(length = 200) private String voidReason;
    @Column(length = 50) private String voidedBy;
    private LocalDateTime voidedAt;
}
