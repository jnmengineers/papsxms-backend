package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A kind of leave (annual, sick, maternity…) and its rules. Editable by the admin; starts from default-settings.json. */
@Entity
@Table(name = "leave_types")
@Getter
@Setter
@NoArgsConstructor
public class LeaveType {
    @Id
    @Column(length = 20)
    private String code;                         // ANNUAL, SICK, MATERNITY…

    @Column(nullable = false, length = 60) private String name;
    /** Days allowed per leave year; empty = no limit (e.g. unpaid leave). */
    private Integer daysPerYear;
    /** true = count Monday–Friday only, skipping public holidays; false = every calendar day (e.g. maternity). */
    @Column(nullable = false) private boolean workingDays = true;
    @Column(nullable = false) private boolean paid = true;
    /** FEMALE / MALE when only one may take it (maternity, paternity); empty = everyone. */
    @Column(length = 10) private String genderOnly;
    /** Unused days that may be carried into the next year (0 = none). */
    @Column(nullable = false) private int carryOverMax = 0;
    /** Someone who joined during the year gets a share of the year's days. */
    @Column(nullable = false) private boolean proRata = false;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private int sortOrder = 0;
    @Column(length = 200) private String notes;
    /** Short permission: a few HOURS on one day (from–to time), not days. Not taken from Act leave. */
    private Boolean byHours;          // empty = no (whole days)
    /** "Other": the applicant says what it is for, and HR confirms/specifies it on approval. */
    private Boolean specify;          // empty = no

    // Optional columns (safe to add to a table that already has rows); empty means "no"
    public boolean isByHours() { return Boolean.TRUE.equals(byHours); }
    public boolean isSpecify() { return Boolean.TRUE.equals(specify); }
}
