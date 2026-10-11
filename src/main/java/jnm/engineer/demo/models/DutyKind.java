package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A kind of duty on the weekly rota (e.g. Teacher on duty). Starts from default-settings.json → "duty". */
@Entity
@Table(name = "duty_kinds")
@Getter
@Setter
@NoArgsConstructor
public class DutyKind {
    @Id
    @Column(length = 20)
    private String code;
    @Column(nullable = false, length = 60) private String name;
    /** How many staff each week. */
    private Integer peoplePerWeek;
    /** Teaching staff only (e.g. teacher on duty)? Empty = yes. */
    private Boolean teachersOnly;
    private Boolean active;
    private Integer sortOrder;
    @Column(length = 200) private String notes;

    public int people() { return peoplePerWeek == null || peoplePerWeek < 1 ? 1 : peoplePerWeek; }
    public boolean isActive() { return active == null || active; }
    public boolean onlyTeachers() { return teachersOnly == null || teachersOnly; }
}
