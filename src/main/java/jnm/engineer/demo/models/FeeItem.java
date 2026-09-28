package jnm.engineer.demo.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** One charge line, e.g. "Tuition" 15,000.00. Used in fee structures and copied onto each invoice. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FeeItem {
    @Column(name = "item_name", nullable = false, length = 80)
    private String name;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
}
