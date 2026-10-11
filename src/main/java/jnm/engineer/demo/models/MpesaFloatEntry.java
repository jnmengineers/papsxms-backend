package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Money put on (or taken off) the school's M-Pesa line, other than bill payments:
 *   TOPUP  — cash from a float cheque deposited on the line
 *   ADJUST — opening balance, or a correction (amount may be negative)
 * Bill payments from the line are expenses with fromFloat = true.
 */
@Entity
@Table(name = "mpesa_float_entries")
@Getter
@Setter
@NoArgsConstructor
public class MpesaFloatEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long entryId;

    @Column(nullable = false) private LocalDate entryDate;
    @Column(nullable = false, length = 10) private String kind;          // TOPUP, ADJUST
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    private Long chequeId;                                                // TOPUP: the float cheque
    @Column(length = 200) private String note;
    @Column(length = 50) private String recordedBy;
    @Column(nullable = false) private LocalDateTime recordedAt;
    @Column(nullable = false) private boolean cancelled = false;
}
