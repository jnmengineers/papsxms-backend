package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A money group ("vote head"): Tuition & Fees, Meals, Transport, Trips, Swimming…
 * Charges AND expenses belong to groups, so each group's profit can be stated.
 * priority: 1 = paid first — a learner's payment clears the oldest term first and,
 * within a term, the groups in this order. Edited on Finance → Setup.
 */
@Entity
@Table(name = "money_groups")
@Getter
@Setter
@NoArgsConstructor
public class MoneyGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long groupId;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(nullable = false, length = 9)
    private String color;

    @Column(nullable = false)
    private Integer priority;

    @Column(nullable = false)
    private boolean active = true;

    /** Fee-structure items and extra charges not given a group go here (exactly one group). */
    @Column(nullable = false)
    private boolean feesDefault = false;

    /** Transport fares go here (exactly one group). */
    @Column(nullable = false)
    private boolean transport = false;
}
