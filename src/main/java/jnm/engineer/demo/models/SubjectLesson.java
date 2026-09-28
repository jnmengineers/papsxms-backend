package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalTime;

/** How many lessons a week a subject gets in a grade (all streams of the grade), e.g. G5 Mathematics = 5. */
@Entity
@Table(name = "subject_lessons",
        uniqueConstraints = @UniqueConstraint(name = "uk_grade_subject", columnNames = {"grade_level", "subject_id"}))
@Getter
@Setter
@NoArgsConstructor
public class SubjectLesson {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grade_level", nullable = false, length = 20)
    private String gradeLevel;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Subject subject;

    @Column(nullable = false)
    private Integer lessonsPerWeek;

    /** Every lesson of this subject must END by this time (e.g. Mathematics by 12:00). Empty = any time. */
    private LocalTime latestEnd;

    /** Two lessons of this subject back-to-back allowed? The Ministry allows it only for some practical
     *  Junior School subjects (e.g. Integrated Science); off by default. */
    @Column
    private Boolean doublesAllowed;
}
