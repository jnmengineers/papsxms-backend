package jnm.engineer.demo.models;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A learner using school transport for one term: destination, one/two ways, bus,
 * and the termly fare charged to their account. The fare and description are COPIED
 * when charged, so changing fares later never changes what was billed.
 * Changing or removing transport CANCELS the entry (kept on record, not counted).
 */
@Entity
@Table(name = "transport_subscriptions",
        indexes = {@Index(name = "ix_transport_term", columnList = "year_label, term"),
                   @Index(name = "ix_transport_student", columnList = "student_id")})
@Getter
@Setter
@NoArgsConstructor
public class TransportSubscription {

    public enum Direction { ONE_WAY, TWO_WAY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long subscriptionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private TransportRoute route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;              // may be empty until buses are assigned

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)   // plain text column, not a MySQL ENUM — new values need no ALTER TABLE
    @Column(nullable = false, length = 10)
    private Direction direction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private SchoolClass schoolClass;      // class when charged

    @Column(name = "year_label", nullable = false, length = 10)
    private String yearLabel;

    @Column(nullable = false)
    private Integer term;

    @Column(nullable = false, length = 100)
    private String description;           // e.g. "Transport — Buruburu (two ways)"

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;            // termly fare at the time

    // ── how it was priced (empty basis = full term; see ChargePricing) ──
    @Column(length = 10) private String basis;
    private Integer quantity;                                        // months or days
    @Column(precision = 12, scale = 2) private BigDecimal rate;      // monthly / daily rate used
    @Column(length = 200) private String priceNote;                  // why (required for an agreed amount)
    @Column(length = 50) private String createdBy;
    @Column(nullable = false) private LocalDateTime createdAt;

    @Column(nullable = false) private boolean cancelled = false;
    @Column(length = 200) private String cancelReason;
    @Column(length = 50) private String cancelledBy;
    private LocalDateTime cancelledAt;
}
