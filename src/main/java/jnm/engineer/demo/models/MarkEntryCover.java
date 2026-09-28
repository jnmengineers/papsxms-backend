package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Temporary permission for a teacher to enter MARKS for a class they don't normally
 * teach — e.g. covering for an absent colleague. One class; one subject or all subjects;
 * active until the end of validUntil, then it switches off by itself.
 * It covers marks only (not learners, report cards or the register).
 */
@Entity
@Table(name = "mark_entry_covers",
        indexes = @Index(name = "ix_cover_user_until", columnList = "cover_user_id, valid_until"))
@Getter
@Setter
@NoArgsConstructor
public class MarkEntryCover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long coverId;

    /** The teacher who may enter the marks. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cover_user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User coverUser;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SchoolClass schoolClass;

    /** Empty = every subject of the class. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Subject subject;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(length = 150)
    private String note;

    @Column(length = 50)  private String grantedBy;       // username
    @Column(length = 100) private String grantedByName;   // shown to people
    @Column(nullable = false) private LocalDateTime createdAt;

    @Column(nullable = false) private boolean revoked = false;
    @Column(length = 50) private String revokedBy;
    private LocalDateTime revokedAt;
}
