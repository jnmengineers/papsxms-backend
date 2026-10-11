package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
/** A fee reminder sent to a family (one line per child), so the bursar sees who was reminded and when. */
@Entity
@Table(name = "fee_reminder_log", indexes = @Index(name = "ix_reminder_guardian", columnList = "guardian_id"))
@Getter
@Setter
@NoArgsConstructor
public class FeeReminderLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;
    @Column(name = "guardian_id") private Long guardianId;
    private Long studentId;
    @Column(nullable = false) private LocalDate sentOn;
    @Column(length = 10) private String channel;            // WHATSAPP, SMS, CALL, OTHER
    @Column(precision = 12, scale = 2) private BigDecimal balance;
    @Column(length = 50) private String sentBy;
}
