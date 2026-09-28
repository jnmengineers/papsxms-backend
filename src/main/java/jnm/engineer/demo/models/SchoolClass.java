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

    @Column(nullable = false)
    private String gradeLevel;

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private Double meanTarget;

    // The ONE place a class teacher is stored. Logins follow this (see SchoolClassController).
    @JsonIgnoreProperties({"classTeacher", "studentList", "hibernateLazyInitializer", "handler", "subjects"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "class_teacher_id")
    private Teacher classTeacher;

    // SAFETY FIX: cascade removed. It used to be CascadeType.ALL, which meant deleting a
    // class also DELETED every student in it. Classes with students can no longer be
    // deleted at all (SchoolClassController refuses) — move the students first.
    @JsonIgnore
    @OneToMany(mappedBy = "schoolClass", fetch = FetchType.LAZY)
    private List<Student> studentList = new ArrayList<>();
}
