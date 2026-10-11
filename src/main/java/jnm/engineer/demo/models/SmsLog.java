package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Every SMS the system sent (or tried to): to whom, what, and what the provider answered. */
@Entity
@Table(name = "sms_log", indexes = @Index(name = "ix_sms_log_sent", columnList = "sent_at"))
@Getter
@Setter
@NoArgsConstructor
public class SmsLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long smsId;
    @Column(length = 20) private String phone;
    @Column(length = 1000) private String message;
    @Column(length = 20) private String status;          // Success, or the provider's status / FAILED
    @Column(length = 30) private String cost;
    @Column(length = 80) private String messageId;
    @Column(length = 300) private String error;
    private Long guardianId;
    @Column(name = "sent_at") private LocalDateTime sentAt;
    @Column(length = 50) private String sentBy;
}
