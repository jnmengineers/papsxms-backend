package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "academicYear")
@Data
@NoArgsConstructor
@AllArgsConstructor

public class AcademicYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long yearId;

    @Column(nullable = false, length = 10)
    private String yearLabel; //e.g "2026"

    @Column(nullable = false)
    private Integer term; //1,2 or 3

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Boolean isActive = false;
}
