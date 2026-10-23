package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One person's answers to a Survey. */
@Entity
@Table(name = "survey_responses", indexes = @Index(name = "ix_sr_survey", columnList = "surveyId"))
@Getter
@Setter
@NoArgsConstructor
public class SurveyResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long surveyId;
    @Column(length = 100) private String respondentName;
    @Column(length = 20) private String phone;
    @Column(columnDefinition = "MEDIUMTEXT") private String answers;   // {questionId: value | [values]}
    private LocalDateTime createdAt;
}
