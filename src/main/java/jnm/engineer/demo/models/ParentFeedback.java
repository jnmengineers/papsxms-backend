package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A parent's feedback from the website form (ratings + comments; name optional). */
@Entity
@Table(name = "parent_feedback")
@Getter
@Setter
@NoArgsConstructor
public class ParentFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime createdAt;
    @Column(length = 100) private String parentName;
    @Column(length = 20) private String phone;
    @Column(length = 60) private String className;
    /** Ratings and choices as JSON, e.g. {"teaching":5,"communication":4,"recommend":"Yes"} */
    @Column(columnDefinition = "TEXT") private String answers;
    @Column(length = 1500) private String doingWell;
    @Column(length = 1500) private String improve;
}
