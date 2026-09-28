package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** A notice from the office to staff. Shown from startsOn until endsOn (if set), to the chosen roles. */
@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
public class Announcement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long announcementId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 4000)
    private String body;

    /** Roles that see it, e.g. "TEACHER,CLERK"; "ALL" = every staff account. */
    @Column(nullable = false, length = 100)
    private String audience = "ALL";

    @Column(nullable = false)
    private boolean pinned = false;

    /** URGENT notices are shown in red. */
    @Column(nullable = false)
    private boolean urgent = false;

    @Column(nullable = false)
    private LocalDate startsOn;

    private LocalDate endsOn;

    @Column(nullable = false)
    private boolean archived = false;

    @Column(length = 50) private String createdBy;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public List<String> audienceList() {
        return Arrays.stream(String.valueOf(audience).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public boolean isFor(String role) {
        List<String> a = audienceList();
        return a.contains("ALL") || (role != null && a.contains(role));
    }
}
