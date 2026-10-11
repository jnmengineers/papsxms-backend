package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
/** One person on one duty for one week (weekStart = that week's Monday). */
@Entity
@Table(name = "duty_assignments", indexes = @Index(name = "ix_duty_week", columnList = "week_start"))
@Getter
@Setter
@NoArgsConstructor
public class DutyAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long assignmentId;
    @Column(name = "week_start", nullable = false) private LocalDate weekStart;
    @Column(nullable = false, length = 20) private String dutyCode;
    @ManyToOne(optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private StaffMember staff;
    @Column(length = 100) private String note;
}
