package jnm.engineer.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A professional document (PDF) attached to a job application: certificate, TSC registration, testimonial… */
@Entity
@Table(name = "job_application_docs", indexes = @Index(name = "ix_jad_app", columnList = "applicationId"))
@Getter
@Setter
@NoArgsConstructor
public class JobApplicationDoc {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long docId;
    private Long applicationId;
    @Column(length = 120) private String fileName;
    @JsonIgnore @Lob @Column(columnDefinition = "LONGBLOB") private byte[] data;
}
