package jnm.engineer.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subject")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Subject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long subjectId;

    @NotBlank(message = "Subject name is required")
    @Column(nullable = false)
    private String subjectName;

    @NotBlank(message = "Subject code is required")
    @Column(nullable = false)
    private String subjectCode;

    @NotBlank(message = "Grade level is required")
    @Column(nullable = false)
    private String gradeLevel;

    // ✅ Changed to EAGER — fixes lazy proxy serialization error
    @JsonIgnoreProperties({"subjects", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;
}