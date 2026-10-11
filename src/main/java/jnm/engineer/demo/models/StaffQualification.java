package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** A staff member's qualification: e.g. Degree — B.Ed (Arts) — Kenyatta University — 2015. */
@Entity
@Table(name = "staff_qualifications")
@Getter
@Setter
@NoArgsConstructor
public class StaffQualification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long qualificationId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private StaffMember staff;

    @Column(nullable = false, length = 30) private String level;      // PhD, Masters, Degree, Diploma, Certificate, KCSE, Other
    @Column(nullable = false, length = 100) private String title;     // B.Ed (Arts), P1 Certificate, Food & Beverage…
    @Column(length = 100) private String institution;
    private Integer yearCompleted;
}
