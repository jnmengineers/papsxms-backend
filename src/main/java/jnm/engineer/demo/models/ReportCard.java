package jnm.engineer.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "reportCard", uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "exam_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor

public class ReportCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    @JsonIgnoreProperties({"schoolClass", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @JsonIgnoreProperties({"academicYearRef", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @Column
    private Integer termRank;

    @Column
    private Integer classRank;

    @Column (nullable = false)
    private Double totalMarks;

    @Column(nullable = false)
    private Double averageMarks;

    @Column(length = 500)
    private String Remarks;

    @Column(length = 500)
    private String teacherComment;

    @Column(length = 500)
    private String principalComment;
}
