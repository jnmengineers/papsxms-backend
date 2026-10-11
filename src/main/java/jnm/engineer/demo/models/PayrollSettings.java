package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** The statutory rates (PAYE bands, relief, NSSF, SHIF, Housing Levy) as JSON — one row. */
@Entity
@Table(name = "payroll_settings")
@Getter
@Setter
@NoArgsConstructor
public class PayrollSettings {
    @Id
    private Long id = 1L;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String ratesJson;

    @Column(length = 50) private String updatedBy;
    private LocalDateTime updatedAt;
}
