package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Who teaches which subject to which class.
 * Used for Upper Primary and Junior School, where teachers teach specific subjects.
 * (Pre-School and Lower Primary use the class teacher for every subject.)
 *
 * One teacher per subject per class — enforced by the unique constraint.
 *
 * Deleting a teacher, class or subject automatically deletes its assignments
 * (ON DELETE CASCADE), so those deletes are never blocked by this table.
 *
 * NOTE: If your class entity is not called SchoolClass, or its id field is not
 * classId, adjust the type below to match. (Teacher.teacherId is confirmed.)
 */
@Entity
@Table(name = "teaching_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uk_class_subject", columnNames = {"class_id", "subject_id"}))
@Getter
@Setter
@NoArgsConstructor
public class TeachingAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long assignmentId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Teacher teacher;

    @ManyToOne(optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SchoolClass schoolClass;

    @ManyToOne(optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Subject subject;
}
