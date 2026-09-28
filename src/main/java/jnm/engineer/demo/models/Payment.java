package jnm.engineer.demo.models;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Money received for a learner. Never deleted — a wrong or bounced payment is
 * REVERSED (with a reason), so the full history is always kept.
 */
@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_receipt", columnNames = {"receipt_number"}),
        indexes = @Index(name = "ix_payment_reference", columnList = "method, reference"))
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    public enum Method { MPESA, BANK, CHEQUE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @Column(name = "receipt_number", length = 30)
    private String receiptNumber;          // e.g. RCT-2026-00042 (set right after saving)

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)   // plain text column, not a MySQL ENUM — new values need no ALTER TABLE
    @Column(nullable = false, length = 10)
    private Method method;

    @Column(nullable = false, length = 40)
    private String reference;              // M-Pesa code, bank slip number or cheque number

    @Column(length = 100)
    private String payerName;              // who paid (optional)

    @Column(nullable = false)
    private LocalDate paidOn;

    @Column(length = 50)
    private String recordedBy;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    // ── reversal (bounced cheque, wrong entry) ──
    @Column(nullable = false)
    private boolean reversed = false;

    @Column(length = 200)
    private String reversalReason;

    @Column(length = 50)
    private String reversedBy;

    private LocalDateTime reversedAt;
}
