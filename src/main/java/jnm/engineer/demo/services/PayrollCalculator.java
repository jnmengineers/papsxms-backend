package jnm.engineer.demo.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * One employee's monthly pay, Kenya rules. Plain Java (no database) so it can be tested alone.
 * Every rate comes from the Rates passed in (Payroll → Rates, from default-settings.json) — none
 * are fixed here, because they change with Finance Acts and NSSF phases.
 *
 *   gross         = basic + all earnings
 *   NSSF          = rate × pensionable pay, capped at the upper limit            (employer pays the same)
 *   SHIF          = rate × gross, at least the minimum
 *   Housing Levy  = rate × gross                                                  (employer pays its own rate)
 *   taxable pay   = taxable earnings − the statutory deductions allowed before PAYE
 *                   (since 27 Dec 2024: NSSF, SHIF and the Housing Levy)
 *   PAYE          = bands on taxable pay − personal relief (never below 0)
 *   net           = gross − NSSF − SHIF − Housing Levy − PAYE − other deductions
 */
public final class PayrollCalculator {
    private PayrollCalculator() {}

    public record Band(BigDecimal upTo, BigDecimal ratePercent) {}          // upTo null = no upper limit
    public record Rates(List<Band> payeBands, BigDecimal personalRelief,
                        BigDecimal nssfRate, BigDecimal nssfUpperLimit,
                        BigDecimal shifRate, BigDecimal shifMinimum,
                        BigDecimal housingRate, BigDecimal housingEmployerRate,
                        boolean nssfBeforePaye, boolean shifBeforePaye, boolean housingBeforePaye) {}
    public record Item(String name, BigDecimal amount, boolean taxable) {}  // taxable: earnings only

    public record Result(BigDecimal basic, BigDecimal gross, BigDecimal taxableEarnings,
                         BigDecimal nssf, BigDecimal shif, BigDecimal housingLevy,
                         BigDecimal taxablePay, BigDecimal payeBeforeRelief, BigDecimal relief, BigDecimal paye,
                         BigDecimal otherDeductions, BigDecimal totalDeductions, BigDecimal net,
                         BigDecimal employerNssf, BigDecimal employerHousingLevy, BigDecimal employerCost,
                         List<String> bandLines) {}

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static BigDecimal pct(BigDecimal amount, BigDecimal rate) {
        if (amount == null || rate == null) return BigDecimal.ZERO;
        return amount.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
    private static BigDecimal z(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** statutory = false for people outside NSSF/SHIF/Housing Levy (e.g. some casual workers); PAYE still applies. */
    public static Result compute(BigDecimal basic, List<Item> earnings, List<Item> deductions, boolean statutory, Rates r) {
        BigDecimal b = z(basic).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gross = b, taxableEarnings = b;
        for (Item e : earnings == null ? List.<Item>of() : earnings) {
            gross = gross.add(z(e.amount()));
            if (e.taxable()) taxableEarnings = taxableEarnings.add(z(e.amount()));
        }
        BigDecimal nssf = BigDecimal.ZERO, shif = BigDecimal.ZERO, housing = BigDecimal.ZERO, housingEmployer = BigDecimal.ZERO;
        if (statutory) {
            BigDecimal pensionable = r.nssfUpperLimit() == null ? gross : gross.min(r.nssfUpperLimit());
            nssf = pct(pensionable, r.nssfRate());
            shif = pct(gross, r.shifRate());
            if (r.shifMinimum() != null && gross.signum() > 0 && shif.compareTo(r.shifMinimum()) < 0) shif = r.shifMinimum().setScale(2, RoundingMode.HALF_UP);
            housing = pct(gross, r.housingRate());
            housingEmployer = pct(gross, r.housingEmployerRate());
        }
        BigDecimal taxablePay = taxableEarnings
                .subtract(r.nssfBeforePaye() ? nssf : BigDecimal.ZERO)
                .subtract(r.shifBeforePaye() ? shif : BigDecimal.ZERO)
                .subtract(r.housingBeforePaye() ? housing : BigDecimal.ZERO)
                .max(BigDecimal.ZERO);

        // PAYE bands
        BigDecimal tax = BigDecimal.ZERO, lower = BigDecimal.ZERO;
        List<String> lines = new ArrayList<>();
        for (Band band : r.payeBands()) {
            if (taxablePay.compareTo(lower) <= 0) break;
            BigDecimal top = band.upTo() == null ? taxablePay : taxablePay.min(band.upTo());
            BigDecimal slice = top.subtract(lower);
            if (slice.signum() > 0) {
                BigDecimal t = pct(slice, band.ratePercent());
                tax = tax.add(t);
                lines.add(band.ratePercent().stripTrailingZeros().toPlainString() + "% of " + slice.setScale(2, RoundingMode.HALF_UP).toPlainString() + " = " + t.toPlainString());
            }
            if (band.upTo() == null) break;
            lower = band.upTo();
        }
        tax = tax.setScale(2, RoundingMode.HALF_UP);
        BigDecimal relief = z(r.personalRelief()).min(tax);
        BigDecimal paye = tax.subtract(relief);

        BigDecimal other = BigDecimal.ZERO;
        for (Item d : deductions == null ? List.<Item>of() : deductions) other = other.add(z(d.amount()));
        other = other.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDed = nssf.add(shif).add(housing).add(paye).add(other);
        BigDecimal net = gross.subtract(totalDed);
        BigDecimal employerNssf = nssf;   // the employer matches the employee's NSSF
        BigDecimal employerCost = gross.add(employerNssf).add(housingEmployer);
        return new Result(b, gross, taxableEarnings, nssf, shif, housing, taxablePay, tax, relief, paye,
                other, totalDed, net, employerNssf, housingEmployer, employerCost, lines);
    }
}
