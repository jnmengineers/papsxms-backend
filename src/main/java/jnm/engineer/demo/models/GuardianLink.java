package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
/** A child linked to a guardian (a learner may have several guardians; a guardian several children). */
@Entity
@Table(name = "guardian_links", uniqueConstraints = @UniqueConstraint(name = "uk_guardian_student", columnNames = {"guardian_id", "student_id"}),
       indexes = @Index(name = "ix_guardian_link_student", columnList = "student_id"))
@Getter
@Setter
@NoArgsConstructor
public class GuardianLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long linkId;
    @ManyToOne(optional = false)
    @JoinColumn(name = "guardian_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Guardian guardian;
    @Column(name = "student_id", nullable = false) private Long studentId;
}
