package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A copy of every deleted mark: who deleted it and when — so it can be checked and restored. */
@Entity
@Table(name = "deleted_marks", indexes = @Index(name = "ix_deleted_marks_at", columnList = "deletedAt"))
@Getter
@Setter
@NoArgsConstructor
public class DeletedMark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long studentId;
    private Long subjectId;
    private Long examId;
    private Double marksObtained;
    private Double maxMarks;
    @Column(length = 120) private String learnerName;
    @Column(length = 30) private String admissionNumber;
    @Column(length = 80) private String className;
    @Column(length = 100) private String subjectName;
    @Column(length = 120) private String examName;
    @Column(length = 50) private String deletedBy;
    private LocalDateTime deletedAt;
    @Column(length = 50) private String restoredBy;
    private LocalDateTime restoredAt;
}
