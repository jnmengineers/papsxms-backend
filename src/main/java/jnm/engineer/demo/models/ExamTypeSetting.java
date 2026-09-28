package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An exam type (Opening, Mid Term, End Term, Extra…). "core" types are the ones expected every term. */
@Entity
@Table(name = "setting_exam_types")
@Getter
@Setter
@NoArgsConstructor
public class ExamTypeSetting {
    @Id @Column(length = 20) private String code;           // OPENING, MID_TERM, END_TERM, EXTRA
    @Column(nullable = false, length = 40) private String name;
    @Column(nullable = false, length = 9)  private String color;
    @Column(nullable = false) private Integer sortOrder;
    @Column(nullable = false) private boolean core = true;
    @Column(nullable = false) private boolean active = true;
}
