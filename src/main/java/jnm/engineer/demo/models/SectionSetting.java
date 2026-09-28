package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A school section. The CODE never changes (it is stored on classes, fees…); name, colour and target can. */
@Entity
@Table(name = "setting_sections")
@Getter
@Setter
@NoArgsConstructor
public class SectionSetting {
    @Id @Column(length = 30) private String code;          // PRE_SCHOOL, LOWER_PRIMARY, …
    @Column(nullable = false, length = 60) private String name;
    @Column(nullable = false, length = 9)  private String color;       // #RRGGBB
    @Column(nullable = false) private Double meanTarget;
    @Column(nullable = false) private Integer sortOrder;
    /** true = subject teachers (e.g. Upper Primary, Junior); false = one class teacher teaches everything. */
    @Column private Boolean subjectTeaching;
}
