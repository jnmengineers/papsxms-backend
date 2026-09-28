package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A school bus / van. */
@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long vehicleId;

    @Column(nullable = false, unique = true, length = 15)
    private String registration;          // e.g. KDA 123X

    @Column(length = 40)
    private String name;                  // e.g. "Bus 1"

    private Integer capacity;

    @Column(length = 80)
    private String driverName;

    @Column(length = 20)
    private String driverPhone;

    @Column(nullable = false)
    private boolean active = true;
}
