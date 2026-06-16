package jnm.engineer.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "examSchedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scheduleId;

    // ✅ Changed to EAGER — fixes lazy proxy serialization error
    @JsonIgnoreProperties({"teacher", "gradeLevel", "subjectCode", "hibernateLazyInitializer", "handler", "subjects"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @JsonIgnoreProperties({"classTeacher", "studentList", "stream", "gradeLevel", "hibernateLazyInitializer", "handler", "subjects"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @JsonIgnoreProperties({"academicYearRef", "startDate", "endDate", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(nullable = false)
    private LocalDate examDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(length = 100)
    private String venue;
}