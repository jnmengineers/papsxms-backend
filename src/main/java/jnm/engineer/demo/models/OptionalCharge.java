package jnm.engineer.demo.models;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.List;

/**
 * A charge that only SOME learners pay: lunch, porridge, meals (each term, opt-in),
 * or interview, admission, advent melody, newcomer fee (once).
 * The list lives in the database and is edited on Finance → Extra Charges.
 */
@Entity
@Table(name = "optional_charges")
@Getter
@Setter
@NoArgsConstructor
public class OptionalCharge {

    public enum Kind { PER_TERM, ONCE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long chargeId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Optional rates for learners who take it for part of a term (empty = not offered). */
    @Column(precision = 12, scale = 2) private BigDecimal monthlyAmount;
    @Column(precision = 12, scale = 2) private BigDecimal dailyAmount;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)   // plain text column, not a MySQL ENUM — new values need no ALTER TABLE
    @Column(nullable = false, length = 10)
    private Kind kind;

    /** Sections it applies to, comma-separated, e.g. "PRE_SCHOOL,LOWER_PRIMARY,UPPER_PRIMARY". */
    @Column(nullable = false, length = 120)
    private String sections;

    @Column(nullable = false)
    private boolean active = true;

    /** Different price per section, e.g. "PRE_SCHOOL:1500.00,UPPER_PRIMARY:2500.00". Empty = {@link #amount} everywhere. */
    @Column(length = 400)
    private String sectionAmounts;

    /** Only these classes, e.g. "3,7,12". Empty = every class in the sections. */
    @Column(length = 500)
    private String classIds;

    /** Money group for reports (e.g. Lunch → Meals). Empty = the fees default group. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id")
    private MoneyGroup group;

    /** Class teachers may tick learners for this (own class, current term). */
    @Column
    private Boolean teacherCanTick;

    /** Price for a learner in this section: the section's own price if set, otherwise the normal amount. */
    public BigDecimal amountFor(String section) {
        BigDecimal v = sectionAmountMap().get(section);
        return v != null ? v : amount;
    }

    public Map<String, BigDecimal> sectionAmountMap() {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        if (sectionAmounts == null || sectionAmounts.isBlank()) return m;
        for (String part : sectionAmounts.split(",")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            try { m.put(kv[0].trim(), new BigDecimal(kv[1].trim())); } catch (NumberFormatException ignored) { }
        }
        return m;
    }

    public Set<Long> classIdSet() {
        Set<Long> ids = new LinkedHashSet<>();
        if (classIds == null || classIds.isBlank()) return ids;
        for (String part : classIds.split(",")) {
            try { ids.add(Long.valueOf(part.trim())); } catch (NumberFormatException ignored) { }
        }
        return ids;
    }

    /** Does this charge apply to a learner in this class? (section, and the class list if one is set) */
    public boolean appliesTo(SchoolClass cls) {
        if (cls == null || !sectionList().contains(cls.getSection())) return false;
        Set<Long> only = classIdSet();
        return only.isEmpty() || only.contains(cls.getClassId());
    }

    public boolean teachersMayTick() { return Boolean.TRUE.equals(teacherCanTick); }

    public List<String> sectionList() {
        return Arrays.stream(sections.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
