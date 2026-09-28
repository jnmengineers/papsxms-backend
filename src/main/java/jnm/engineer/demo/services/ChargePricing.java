package jnm.engineer.demo.services;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * How a learner is charged for a service (meals, transport, …) in a term.
 *   TERM   — the normal termly price
 *   MONTHS — number of months × the monthly rate
 *   DAYS   — number of days × the daily rate
 *   CUSTOM — an amount the accountant agrees (a note saying why is required)
 * The rates come from the charge / destination settings; nothing is fixed here.
 */
public final class ChargePricing {
    public enum Basis { TERM, MONTHS, DAYS, CUSTOM }

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");
    private static final int MAX_MONTHS = 12;
    private static final int MAX_DAYS = 200;

    private ChargePricing() {}

    public record Price(Basis basis, Integer quantity, BigDecimal rate, BigDecimal amount, String note) {
        /** Same charge as an existing entry? (basis, quantity and amount all equal) */
        public boolean sameAs(String otherBasis, Integer otherQuantity, BigDecimal otherAmount) {
            return basis == parseStored(otherBasis)
                    && Objects.equals(quantity, otherQuantity)
                    && otherAmount != null && amount.compareTo(otherAmount) == 0;
        }
        public String label() { return ChargePricing.label(basis.name(), quantity); }
    }

    /** Stored value → basis (empty = TERM, for entries made before this existed). */
    public static Basis parseStored(String stored) {
        if (stored == null || stored.isBlank()) return Basis.TERM;
        try { return Basis.valueOf(stored.trim().toUpperCase()); } catch (IllegalArgumentException e) { return Basis.TERM; }
    }

    /** Short description added after the charge name, e.g. " (2 months)". Empty for a full term. */
    public static String label(String stored, Integer quantity) {
        return switch (parseStored(stored)) {
            case TERM -> "";
            case MONTHS -> " (" + quantity + (Objects.equals(quantity, 1) ? " month)" : " months)");
            case DAYS -> " (" + quantity + (Objects.equals(quantity, 1) ? " day)" : " days)");
            case CUSTOM -> " (agreed amount)";
        };
    }

    /**
     * Works out the amount. {@code what} names the service in error messages, e.g. "Lunch".
     * Monthly / daily rates may be null (not set) — then that option is refused with a clear message.
     */
    public static Price resolve(String basisIn, Integer quantity, BigDecimal customAmount, String noteIn,
                                BigDecimal termRate, BigDecimal monthlyRate, BigDecimal dailyRate, String what) {
        Basis basis;
        try { basis = basisIn == null || basisIn.isBlank() ? Basis.TERM : Basis.valueOf(basisIn.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw bad("Choose how " + what + " is charged: full term, months, days or an agreed amount."); }
        String note = noteIn == null || noteIn.isBlank() ? null : noteIn.trim();
        if (note != null && note.length() > 200) note = note.substring(0, 200);

        switch (basis) {
            case TERM -> {
                if (termRate == null) throw bad(what + " has no termly price.");
                return new Price(basis, null, termRate, termRate, note);
            }
            case MONTHS -> {
                if (monthlyRate == null || monthlyRate.signum() <= 0)
                    throw bad("No monthly rate is set for " + what + ". Set one, or use an agreed amount.");
                if (quantity == null || quantity < 1 || quantity > MAX_MONTHS) throw bad("Months must be between 1 and " + MAX_MONTHS + ".");
                return new Price(basis, quantity, monthlyRate, monthlyRate.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP), note);
            }
            case DAYS -> {
                if (dailyRate == null || dailyRate.signum() <= 0)
                    throw bad("No daily rate is set for " + what + ". Set one, or use an agreed amount.");
                if (quantity == null || quantity < 1 || quantity > MAX_DAYS) throw bad("Days must be between 1 and " + MAX_DAYS + ".");
                return new Price(basis, quantity, dailyRate, dailyRate.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP), note);
            }
            default -> {   // CUSTOM
                if (customAmount == null || customAmount.signum() < 0) throw bad("Enter the agreed amount (0 or more).");
                if (customAmount.stripTrailingZeros().scale() > 2) throw bad("The agreed amount can have at most 2 decimal places.");
                if (customAmount.compareTo(MAX_AMOUNT) > 0) throw bad("The agreed amount looks too large — please check it.");
                if (note == null || note.length() < 3) throw bad("Add a short note saying why " + what + " has an agreed amount.");
                return new Price(basis, null, null, customAmount.setScale(2, RoundingMode.HALF_UP), note);
            }
        }
    }

    /** Optional rate from a settings form: empty = not set. */
    public static BigDecimal optionalRate(BigDecimal v, String label) {
        if (v == null || v.signum() == 0) return null;
        if (v.signum() < 0) throw bad(label + " can't be negative.");
        if (v.stripTrailingZeros().scale() > 2) throw bad(label + " can have at most 2 decimal places.");
        if (v.compareTo(MAX_AMOUNT) > 0) throw bad(label + " looks too large — please check it.");
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
