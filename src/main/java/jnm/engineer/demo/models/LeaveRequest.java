package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One leave application: PENDING → APPROVED / REJECTED, or CANCELLED. */
@Entity
@Table(name = "leave_requests", indexes = @Index(name = "ix_leave_staff", columnList = "staff_id"))
@Getter
@Setter
@NoArgsConstructor
public class LeaveRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long leaveId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private StaffMember staff;

    @Column(nullable = false, length = 20) private String typeCode;
    @Column(nullable = false) private LocalDate startDate;
    @Column(nullable = false) private LocalDate endDate;
    /** Days counted against the balance (working or calendar days, by the leave type's rule). */
    @Column(nullable = false) private int days;
    @Column(length = 300) private String reason;
    private Long reliefStaffId;                  // who covers their work (optional)
    @Column(length = 100) private String contactWhileAway;

    @Column(nullable = false, length = 10) private String status = "PENDING";   // PENDING, APPROVED, REJECTED, CANCELLED
    @Column(length = 50) private String appliedBy;
    private LocalDateTime appliedAt;
    @Column(length = 50) private String decidedBy;
    private LocalDateTime decidedAt;
    @Column(length = 200) private String decisionNote;

    /** Short permission (hours): the times on the day and the hours they make. */
    private java.time.LocalTime startTime;
    private java.time.LocalTime endTime;
    @Column(precision = 5, scale = 2) private java.math.BigDecimal hours;
    /** "Other" leave: what it is for (the applicant's words, confirmed or changed by HR on approval). */
    @Column(length = 80) private String otherName;
}
