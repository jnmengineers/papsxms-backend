package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** A public holiday not worked out automatically (e.g. Idd-ul-Fitr, a gazetted holiday). Skipped when counting leave days. */
@Entity
@Table(name = "leave_holidays")
@Getter
@Setter
@NoArgsConstructor
public class LeaveHoliday {
    @Id
    private LocalDate holidayDate;
    @Column(nullable = false, length = 80) private String name;
}
