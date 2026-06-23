package jnm.engineer.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "exam")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Exam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long examId;

    @NotBlank(message = "Exam name is required")
    @Column(nullable = false, length = 100)
    private String examName;

    @NotBlank(message = "Academic year is required")
    @Column(nullable = false, length = 10)
    private String academicYear;

    @NotNull(message = "Term is required")
    @Column(nullable = false)
    private Integer term;

    @NotNull(message = "Start date is required")
    @Column(nullable = false)
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @Column(nullable = false)
    private LocalDate endDate;

    @NotBlank(message = "Class level is required")
    @Column(nullable = false, length = 20)
    private String classLevel;

    // ✅ NEW — identifies exam position within a term
    // Values: OPENING, MID_TERM, END_TERM
    @Column(length = 20)
    private String examType;

    // ✅ Changed to EAGER — fixes lazy proxy serialization error
    @JsonIgnoreProperties({"exams", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYearRef;
}