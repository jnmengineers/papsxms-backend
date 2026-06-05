package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "grading_scales")
@Data
@NoArgsConstructor
@AllArgsConstructor

public class GradingScale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scaleId;


    @Column(nullable = false, length = 5)
    private String gradeLetter ;

    @Column(nullable = false)
    private LocalDate startTime;

    @Column(nullable = false)
    private Double minMark;


    @Column(nullable = false)
    private Double points;

    @Column(length = 100)
    private Double maxMark;

    @Column(length = 100)
    private String remarks;
}

