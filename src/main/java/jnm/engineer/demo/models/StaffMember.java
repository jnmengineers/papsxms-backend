package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Someone the school pays: a teacher (linked to the teacher record, if any) or support staff. */
@Entity
@Table(name = "staff_members")
@Getter
@Setter
@NoArgsConstructor
public class StaffMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long staffId;

    @Column(nullable = false, unique = true, length = 20)
    private String staffNumber;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(length = 60) private String jobTitle;              // e.g. Teacher, Cook, Driver, Bursar
    @Column(nullable = false, length = 20) private String category = "SUPPORT";   // TEACHING, SUPPORT, ADMINISTRATION
    private Long teacherId;                                     // the teacher record, if this is a teacher (optional)

    @Column(length = 20) private String phone;
    @Column(length = 20) private String kraPin;
    @Column(length = 20) private String nssfNumber;
    @Column(length = 20) private String shaNumber;

    @Column(nullable = false, length = 10) private String payMethod = "BANK";     // BANK, MPESA, CASH, CHEQUE
    @Column(length = 60) private String bankName;
    @Column(length = 60) private String bankBranch;
    @Column(length = 40) private String accountNumber;
    /** The bank's code for the salary upload file (Swift or local bank code). */
    @Column(length = 20) private String bankCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    /** false = no NSSF / SHIF / Housing Levy (e.g. some casual workers). PAYE still applies. */
    @Column(nullable = false) private boolean statutory = true;
    @Column(nullable = false) private boolean active = true;
    private LocalDate startDate;
    @Column(length = 200) private String notes;

    // ── Staff records (Staff page) ──
    /** On the school's payroll? Outsourced staff (e.g. agency guards) are not. Empty = yes (records made before this existed). */
    private Boolean onPayroll;
    @Column(length = 10) private String gender;
    private LocalDate dateOfBirth;
    @Column(length = 20) private String idNumber;
    @Column(length = 100) private String email;
    @Column(length = 100) private String residence;

    @Column(length = 10) private String employer;             // SCHOOL (default), AGENCY (outsourced), OTHER
    @Column(length = 20) private String tscNumber;            // teachers: TSC registration number
    @Column(length = 12) private String employmentType;       // PERMANENT, CONTRACT, CASUAL, INTERN, VOLUNTEER
    private LocalDate contractEnd;
    @Column(length = 60) private String department;
    private LocalDate dateLeft;
    @Column(length = 200) private String leftReason;

    @Column(length = 100) private String nextOfKinName;
    @Column(length = 40) private String nextOfKinRelationship;
    @Column(length = 20) private String nextOfKinPhone;

    public boolean isOnPayroll() { return onPayroll == null || onPayroll; }
}
