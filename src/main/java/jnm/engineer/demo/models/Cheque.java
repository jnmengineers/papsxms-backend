package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A cheque the treasurer raised to pay one or more approved bills (expenses). */
@Entity
@Table(name = "cheques")
@Getter
@Setter
@NoArgsConstructor
public class Cheque {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long chequeId;

    @Column(nullable = false, length = 30) private String chequeNumber;
    @Column(nullable = false) private LocalDate chequeDate;
    @Column(nullable = false, length = 100) private String payee;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(length = 200) private String memo;
    /** BILLS = pays suppliers' bills directly; FLOAT = cash withdrawn and put on the school's M-Pesa line (not an expense). Empty = BILLS. */
    @Column(length = 10) private String purpose;

    @Column(length = 50) private String raisedBy;
    @Column(nullable = false) private LocalDateTime raisedAt;

    /** A spoilt or stopped cheque: its bills go back to "to be paid". */
    @Column(nullable = false) private boolean cancelled = false;
    @Column(length = 200) private String cancelReason;
    @Column(length = 50) private String cancelledBy;
    private LocalDateTime cancelledAt;
}
