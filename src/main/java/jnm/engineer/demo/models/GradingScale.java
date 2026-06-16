package jnm.engineer.demo.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "grading_scales")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GradingScale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scaleId;

    @NotBlank(message = "Grade letter is required")
    @Column(nullable = false, length = 5)
    private String gradeLetter;

    @NotNull(message = "Min mark is required")
    @Column(nullable = false)
    private Double minMark;

    @NotNull(message = "Max mark is required")
    @Column(nullable = false)
    private Double maxMark;

    @NotNull(message = "Points are required")
    @Column(nullable = false)
    private Double points;

    @Column(length = 100)
    private String remarks;
}