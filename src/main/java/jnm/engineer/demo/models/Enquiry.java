package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A message from the public website: an admission application or a general enquiry. */
@Entity
@Table(name = "enquiries")
@Getter
@Setter
@NoArgsConstructor
public class Enquiry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long enquiryId;
    @Column(length = 20) private String kind;          // ADMISSION or CONTACT
    @Column(length = 100) private String parentName;
    @Column(length = 20) private String phone;
    @Column(length = 100) private String email;
    @Column(length = 100) private String learnerName;
    @Column(length = 40) private String gradeWanted;
    @Column(length = 1500) private String message;
    @Column(length = 20) private String status;        // NEW, CONTACTED, INTERVIEW, ADMITTED, NOT_ADMITTED, CLOSED
    private java.time.LocalDate interviewDate;
    @Column(length = 20) private String admittedAs;    // admission number given
    @Column(length = 300) private String staffNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Column(length = 50) private String handledBy;
}
