package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Which money group a fee-structure item belongs to, by item name (e.g. "Exam fee" → Tuition & Fees). */
@Entity
@Table(name = "fee_item_groups")
@Getter
@Setter
@NoArgsConstructor
public class FeeItemGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String itemName;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private MoneyGroup group;
}
