package jnm.engineer.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Entity
@Table(name = "student")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long studentId;

    @Column(nullable = false, length = 50)
    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name cannot exceed 50 characters")
    private String firstName;

    @Column(nullable = false, length = 50)
    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name cannot exceed 50 characters")
    private String lastName;

    @Column(nullable = false)
    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 10)
    @NotBlank(message = "Gender is required")
    private String gender;

    @Column(nullable = false, length = 20)
    @NotBlank(message = "Admission number is required")
    private String admissionNumber;

    @Column(nullable = false, length = 20)
    private String className;

    @Column(length = 10)
    private String stream;

    // ✅ Changed to EAGER — fixes lazy proxy serialization error
    @JsonIgnoreProperties({"studentList", "classTeacher", "hibernateLazyInitializer", "handler", "subjects"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "classId")
    private SchoolClass schoolClass;

    // ── Leaving school (transfer etc.). Empty status = in school. Marks, report cards and fees are kept. ──
    /** null / ACTIVE = in school; TRANSFERRED (with consent), DISAPPEARED (without notice), LEFT or GRADUATED */
    @Column(length = 20)
    private String status;
    private LocalDate dateLeft;
    @Column(length = 200)
    private String leftReason;
    /** Did the parent/guardian give notice or consent (transfer letter)? false for DISAPPEARED */
    private Boolean leaveConsent;
    /** Fee balance on the day the learner was marked as left (positive = owed) */
    @Column(precision = 12, scale = 2)
    private java.math.BigDecimal balanceAtLeaving;
    /** The school the learner moved to (transfers) */
    @Column(length = 120)
    private String transferSchool;

    /** True while the learner is in school. */
    @Transient
    @JsonIgnore
    public boolean isInSchool() { return status == null || status.isBlank() || "ACTIVE".equalsIgnoreCase(status); }

    /** True if the learner was in school on that day (left later, or not left at all). */
    @Transient
    @JsonIgnore
    public boolean isInSchoolOn(LocalDate day) { return isInSchool() || (dateLeft != null && day != null && dateLeft.isAfter(day)); }
}
