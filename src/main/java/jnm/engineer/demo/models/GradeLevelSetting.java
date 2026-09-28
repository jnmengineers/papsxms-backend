package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A grade (PG … G9): its full name, section, order, and which grade learners are promoted to. */
@Entity
@Table(name = "setting_grades")
@Getter
@Setter
@NoArgsConstructor
public class GradeLevelSetting {
    @Id @Column(length = 10) private String code;           // PG, PP1, …, G9
    @Column(nullable = false, length = 40) private String name;    // "Play Group", "Grade 4"
    @Column(nullable = false, length = 30) private String sectionCode;
    @Column(nullable = false) private Integer sortOrder;
    @Column(length = 10) private String nextGradeCode;       // empty = graduates (last grade)
}
