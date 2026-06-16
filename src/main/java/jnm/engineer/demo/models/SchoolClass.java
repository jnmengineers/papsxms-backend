package jnm.engineer.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "classes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchoolClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long classId;

    @NotBlank(message = "Class name is required")
    @Column(nullable = false)
    private String className;

    @Column
    private String stream;

    // e.g. PG, PP1, PP2, G1, G2, G3, G4, G5, G6, G7, G8, G9
    @Column(nullable = false)
    private String gradeLevel;

    // PRE_SCHOOL, LOWER_PRIMARY, UPPER_PRIMARY, JUNIOR_SCHOOL
    @Column(nullable = false)
    private String section;

    // Subject mean target based on section
    @Column(nullable = false)
    private Double meanTarget;

    @JsonIgnoreProperties({"classTeacher", "studentList", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_teacher_id")
    private Teacher classTeacher;

    @JsonIgnore
    @OneToMany(mappedBy = "schoolClass", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Student> studentList = new ArrayList<>();
}