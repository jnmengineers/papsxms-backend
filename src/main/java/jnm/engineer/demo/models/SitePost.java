package jnm.engineer.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A news item, event or gallery photo shown on the public website. */
@Entity
@Table(name = "site_posts")
@Getter
@Setter
@NoArgsConstructor
public class SitePost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postId;
    @Column(length = 10) private String kind;            // NEWS, EVENT, GALLERY, SLIDE (background), PERSON (management), PROGRAM
    @Column(length = 150) private String title;
    @Column(length = 30) private String tag;
    @Column(length = 2000) private String body;
    private LocalDate eventDate;
    private Boolean published;
    @JsonIgnore @Lob @Column(columnDefinition = "LONGBLOB") private byte[] imageData;
    @Column(length = 40) private String imageType;
    private LocalDateTime createdAt;
    @Column(length = 50) private String createdBy;

    public boolean isHasImage() { return imageData != null && imageData.length > 0; }
}
