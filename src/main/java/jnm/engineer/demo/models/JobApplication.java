package jnm.engineer.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** An application for a job vacancy, sent from the website (with an optional CV as PDF). */
@Entity
@Table(name = "job_applications")
@Getter
@Setter
@NoArgsConstructor
public class JobApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long applicationId;
    private Long vacancyId;
    @Column(length = 150) private String vacancyTitle;
    @Column(length = 100) private String fullName;
    @Column(length = 20) private String phone;
    @Column(length = 100) private String email;
    @Column(length = 300) private String qualifications;
    @Column(length = 1500) private String message;
    @JsonIgnore @Lob @Column(columnDefinition = "LONGBLOB") private byte[] cvData;
    @Column(length = 120) private String cvName;
    @Column(length = 20) private String status;       // NEW, SHORTLISTED, INTERVIEW, HIRED, NOT_SUCCESSFUL
    @Column(length = 300) private String staffNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Column(length = 50) private String handledBy;

    public boolean isHasCv() { return cvData != null && cvData.length > 0; }
}
