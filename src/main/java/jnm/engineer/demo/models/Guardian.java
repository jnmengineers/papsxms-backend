package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A parent or guardian. Children are linked through GuardianLink; children who share a guardian are siblings. */
@Entity
@Table(name = "guardians")
@Getter
@Setter
@NoArgsConstructor
public class Guardian {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long guardianId;
    @Column(nullable = false, length = 100) private String fullName;
    /** Main phone, stored as +2547XXXXXXXX (used for reminders and WhatsApp). */
    @Column(nullable = false, length = 20) private String phone;
    @Column(length = 20) private String phone2;
    @Column(length = 100) private String email;
    @Column(length = 30) private String relationship;      // Mother, Father, Guardian…
    @Column(length = 200) private String notes;
}
