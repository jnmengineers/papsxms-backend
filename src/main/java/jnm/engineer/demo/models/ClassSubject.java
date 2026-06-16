package jnm.engineer.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "class_subjects",
        uniqueConstraints = @UniqueConstraint(columnNames = {"class_id", "subject_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ Changed to EAGER — fixes lazy proxy serialization error
    @JsonIgnoreProperties({"studentList", "classTeacher", "hibernateLazyInitializer", "handler", "subjects"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @JsonIgnoreProperties({"teacher", "hibernateLazyInitializer", "handler", "subjects"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;
}