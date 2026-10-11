package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
/** A staff member's attendance for one day: PRESENT, LATE, ABSENT, ON_LEAVE or OFF (not expected that day). */
@Entity
@Table(name = "staff_attendance", uniqueConstraints = @UniqueConstraint(name = "uk_staff_attendance_day", columnNames = {"staff_id", "day"}),
       indexes = @Index(name = "ix_staff_attendance_day", columnList = "day"))
@Getter
@Setter
@NoArgsConstructor
public class StaffAttendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attendanceId;
    @ManyToOne(optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private StaffMember staff;
    @Column(name = "day", nullable = false) private LocalDate day;
    @Column(nullable = false, length = 10) private String status;
    private LocalTime timeIn;
    private LocalTime timeOut;
    @Column(length = 150) private String note;
    @Column(length = 50) private String recordedBy;
    private LocalDateTime recordedAt;
}
