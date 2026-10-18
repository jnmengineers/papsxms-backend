package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Invigilation / marking mode: a teacher who marked another class's papers for one exam may enter
 * those marks — that exam, class and subject only, EMPTY marks only (never changing others' marks),
 * until validUntil. Anyone can see active ones; the teacher, the class teacher or the admin can stop it.
 */
@Entity
@Table(name = "invigilations")
@Getter
@Setter
@NoArgsConstructor
public class Invigilation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 50) private String username;
    @Column(length = 100) private String teacherName;
    private Long examId;
    @Column(length = 150) private String examName;
    private Long classId;
    @Column(length = 80) private String className;
    private Long subjectId;
    @Column(length = 100) private String subjectName;
    private LocalDate validUntil;
    private LocalDateTime createdAt;
    private LocalDateTime stoppedAt;
    @Column(length = 50) private String stoppedBy;
}
