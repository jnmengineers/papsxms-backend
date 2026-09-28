package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * One period of a section's school day (the bell times), e.g. Lesson 1 08:00–08:40, Break 10:00–10:20.
 * Each section has its own day because lesson lengths differ (e.g. Pre-School vs Junior School).
 * Set by the admin on Timetable → Setup.
 */
@Entity
@Table(name = "timetable_slots", indexes = @Index(name = "ix_slot_section", columnList = "section"))
@Getter
@Setter
@NoArgsConstructor
public class TimetableSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long slotId;

    @Column(nullable = false, length = 30)
    private String section;             // section code from School Settings

    @Column(nullable = false)
    private Integer position;           // order in the day, 1 = first

    @Column(nullable = false, length = 40)
    private String label;               // "Lesson 1", "Break", "Lunch"

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    /** true = a lesson can be placed here; false = break, lunch, assembly… */
    @Column(nullable = false)
    private boolean lesson = true;
}
