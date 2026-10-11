package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
/** The bursar's reminder message and the smallest balance worth reminding about (one row, id = 1). */
@Entity
@Table(name = "fee_reminder_settings")
@Getter
@Setter
@NoArgsConstructor
public class FeeReminderSettings {
    @Id private Long id = 1L;
    @Column(length = 1000) private String template;
    @Column(precision = 12, scale = 2) private BigDecimal minBalance;
}
