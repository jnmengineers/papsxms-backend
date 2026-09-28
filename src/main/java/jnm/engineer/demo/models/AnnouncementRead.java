package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/** Who has read which notice, and when. */
@Entity
@Table(name = "announcement_reads",
        uniqueConstraints = @UniqueConstraint(name = "uk_read", columnNames = {"announcement_id", "username"}))
@Getter
@Setter
@NoArgsConstructor
public class AnnouncementRead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "announcement_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Announcement announcement;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private LocalDateTime readAt;
}
