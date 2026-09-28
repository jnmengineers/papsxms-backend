package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** The fees for one SECTION in one TERM (e.g. Upper Primary, 2026 Term 1). */
@Entity
@Table(name = "fee_structures",
        uniqueConstraints = @UniqueConstraint(name = "uk_fee_structure", columnNames = {"section", "year_label", "term"}))
@Getter
@Setter
@NoArgsConstructor
public class FeeStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long structureId;

    @Column(nullable = false, length = 30)
    private String section;            // PRE_SCHOOL, LOWER_PRIMARY, UPPER_PRIMARY, JUNIOR_SCHOOL

    @Column(name = "year_label", nullable = false, length = 10)
    private String yearLabel;

    @Column(nullable = false)
    private Integer term;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fee_structure_items", joinColumns = @JoinColumn(name = "structure_id"))
    @OrderColumn(name = "line_no")
    private List<FeeItem> items = new ArrayList<>();

    public BigDecimal getTotal() {
        return items.stream().map(FeeItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
