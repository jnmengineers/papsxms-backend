package jnm.engineer.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One student's attendance on one day.
 * The class is stored WITH the record, so promoting a student later
 * never changes which class's register an old day belongs to.
 */
@Entity
@Table(name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendance_student_date", columnNames = {"student_id", "attendance_date"}),
        indexes = @Index(name = "ix_attendance_class_date", columnList = "class_id, attendance_date"))
@Getter
@Setter
@NoArgsConstructor
public class AttendanceRecord {

    public enum Status { PRESENT, ABSENT, LATE, EXCUSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attendanceId;

    @JsonIgnoreProperties({"schoolClass", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Student student;

    @JsonIgnoreProperties({"classTeacher", "studentList", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SchoolClass schoolClass;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)   // plain text column, not a MySQL ENUM — new values need no ALTER TABLE
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(length = 200)
    private String note;

    @Column(length = 50)
    private String markedBy;

    private LocalDateTime markedAt;
}
