package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

/** A regular monthly allowance (EARNING) or deduction (DEDUCTION) for one staff member. */
@Entity
@Table(name = "staff_pay_items")
@Getter
@Setter
@NoArgsConstructor
public class StaffPayItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long itemId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private StaffMember staff;

    @Column(nullable = false, length = 10) private String kind;      // EARNING or DEDUCTION
    @Column(nullable = false, length = 60) private String name;      // House allowance, Sacco, Salary advance…
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    /** Earnings only: counts towards PAYE (most allowances do). */
    @Column(nullable = false) private boolean taxable = true;
}
