package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One extra charge on one learner's account (e.g. "Lunch — 2026 Term 1", 3,200).
 * Name and amount are COPIED from the charge, so editing the charge later never
 * changes what was already charged. Removing a learner from a list CANCELS the entry
 * (it stays on record, marked cancelled, and no longer counts in the balance).
 */
@Entity
@Table(name = "charge_entries",
        indexes = {@Index(name = "ix_charge_entry_charge_term", columnList = "charge_id, year_label, term"),
                   @Index(name = "ix_charge_entry_student", columnList = "student_id")})
@Getter
@Setter
@NoArgsConstructor
public class ChargeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long entryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_id", nullable = false)
    private OptionalCharge charge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private SchoolClass schoolClass;          // class when charged (history stays right after promotion)

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "year_label", nullable = false, length = 10)
    private String yearLabel;

    @Column(nullable = false)
    private Integer term;

    @Column(length = 50)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // ── how it was priced (empty basis = full term; see ChargePricing) ──
    @Column(length = 10) private String basis;
    private Integer quantity;                                        // months or days
    @Column(precision = 12, scale = 2) private BigDecimal rate;      // monthly / daily rate used
    @Column(length = 200) private String priceNote;                  // why (required for an agreed amount)
    // ── cancellation ──
    @Column(nullable = false)
    private boolean cancelled = false;

    @Column(length = 200)
    private String cancelReason;

    @Column(length = 50)
    private String cancelledBy;

    private LocalDateTime cancelledAt;
}
