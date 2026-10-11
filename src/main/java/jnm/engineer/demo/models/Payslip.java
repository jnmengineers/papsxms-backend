package jnm.engineer.demo.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

/**
 * One person's pay for one run. The person's details and every figure are COPIED here,
 * so a payslip never changes when the staff record or the rates change later.
 */
@Entity
@Table(name = "payslips", indexes = @Index(name = "ix_payslip_run", columnList = "run_id"))
@Getter
@Setter
@NoArgsConstructor
public class Payslip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payslipId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PayrollRun run;

    @Column(nullable = false) private Long staffId;
    @Column(length = 20) private String staffNumber;
    @Column(length = 100) private String fullName;
    @Column(length = 60) private String jobTitle;
    @Column(length = 20) private String category;
    @Column(length = 20) private String kraPin;
    @Column(length = 20) private String nssfNumber;
    @Column(length = 20) private String shaNumber;
    @Column(length = 10) private String payMethod;
    @Column(length = 60) private String bankName;
    @Column(length = 40) private String accountNumber;
    @Column(length = 20) private String bankCode;
    @Column(length = 20) private String phone;

    @Column(precision = 12, scale = 2) private BigDecimal basic;
    @Column(precision = 12, scale = 2) private BigDecimal gross;
    @Column(precision = 12, scale = 2) private BigDecimal taxablePay;
    @Column(precision = 12, scale = 2) private BigDecimal nssf;
    @Column(precision = 12, scale = 2) private BigDecimal shif;
    @Column(precision = 12, scale = 2) private BigDecimal housingLevy;
    @Column(precision = 12, scale = 2) private BigDecimal payeBeforeRelief;
    @Column(precision = 12, scale = 2) private BigDecimal relief;
    @Column(precision = 12, scale = 2) private BigDecimal paye;
    @Column(precision = 12, scale = 2) private BigDecimal otherDeductions;
    @Column(precision = 12, scale = 2) private BigDecimal net;
    @Column(precision = 12, scale = 2) private BigDecimal employerNssf;
    @Column(precision = 12, scale = 2) private BigDecimal employerHousing;
    @Column(precision = 12, scale = 2) private BigDecimal employerCost;

    /** Earnings and deductions shown on the payslip, and the PAYE workings (JSON). */
    @Column(columnDefinition = "TEXT") private String detailJson;
    /** This month only: extra earnings/deductions (overtime, a one-off advance…) (JSON). */
    @Column(columnDefinition = "TEXT") private String adjustmentsJson;
}
