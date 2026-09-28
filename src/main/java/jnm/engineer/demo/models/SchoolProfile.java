package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The school's details used on every page and printout. There is only ever ONE row. */
@Entity
@Table(name = "school_profile")
@Getter
@Setter
@NoArgsConstructor
public class SchoolProfile {

    @Id
    private Long id = 1L;

    @Column(nullable = false, length = 120) private String name;
    @Column(length = 60)  private String shortName;
    @Column(length = 160) private String motto;
    @Column(length = 160) private String postalAddress;
    @Column(length = 120) private String phones;
    @Column(length = 100) private String email;
    @Column(length = 100) private String website;

    // Fee payment details (printed on fee structures, receipts, statements)
    @Column(length = 60)  private String bankName;
    @Column(length = 60)  private String bankBranch;
    @Column(length = 120) private String bankAccountName;
    @Column(length = 40)  private String bankAccountNumber;
    @Column(length = 20)  private String paybillNumber;
    @Column(length = 80)  private String paybillAccountHint;   // e.g. "Pupil's name"
    @Column(length = 200) private String paymentNote;          // e.g. "Cash payment is not allowed"

    // Logos as data URLs (image stored in the database). Empty = use the built-in logos.
    @Lob @Column(columnDefinition = "LONGTEXT") private String logoLeft;
    @Lob @Column(columnDefinition = "LONGTEXT") private String logoRight;
}
