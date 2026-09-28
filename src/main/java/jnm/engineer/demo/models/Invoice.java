package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A term's fees charged to one learner. The lines are COPIED from the fee structure,
 * so changing the structure later never changes what was already billed.
 * One invoice per learner per term (unique) — billing twice is impossible.
 */
@Entity
@Table(name = "invoices",
        uniqueConstraints = @UniqueConstraint(name = "uk_invoice_student_term", columnNames = {"student_id", "year_label", "term"}))
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long invoiceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    // The class the learner was in when billed (history stays correct after promotion)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private SchoolClass schoolClass;

    @Column(name = "year_label", nullable = false, length = 10)
    private String yearLabel;

    @Column(nullable = false)
    private Integer term;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "invoice_lines", joinColumns = @JoinColumn(name = "invoice_id"))
    @OrderColumn(name = "line_no")
    private List<FeeItem> lines = new ArrayList<>();

    @Column(length = 50)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
