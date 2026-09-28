package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** One lesson on a class's timetable: class, day (1 = Monday … 5 = Friday), period, subject, teacher. */
@Entity
@Table(name = "timetable_entries",
        uniqueConstraints = @UniqueConstraint(name = "uk_class_day_slot", columnNames = {"class_id", "day_of_week", "slot_id"}),
        indexes = @Index(name = "ix_entry_teacher", columnList = "teacher_id"))
@Getter
@Setter
@NoArgsConstructor
public class TimetableEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long entryId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SchoolClass schoolClass;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @ManyToOne(optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TimetableSlot slot;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Subject subject;

    /** Empty = no teacher yet (shows as "unassigned"). */
    @ManyToOne
    @JoinColumn(name = "teacher_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Teacher teacher;
}
