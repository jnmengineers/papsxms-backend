package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** A bus destination and its fares (one way / two ways, monthly / termly). Edited on the Transport page. */
@Entity
@Table(name = "transport_routes")
@Getter
@Setter
@NoArgsConstructor
public class TransportRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long routeId;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal oneWayMonthly;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal oneWayTermly;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal twoWayMonthly;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal twoWayTermly;

    /** Optional daily fares for learners who ride only some days (empty = not offered). */
    @Column(precision = 12, scale = 2) private BigDecimal oneWayDaily;
    @Column(precision = 12, scale = 2) private BigDecimal twoWayDaily;
    @Column(nullable = false)
    private boolean active = true;
}
