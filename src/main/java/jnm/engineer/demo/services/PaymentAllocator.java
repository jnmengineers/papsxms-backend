package jnm.engineer.demo.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Splits what a learner has paid across what they were charged:
 *   1. oldest term first,
 *   2. within a term, money groups in the bursar's priority order (1 = first),
 *   3. then oldest charge first.
 * Whatever is left after every charge is covered is credit (paid in advance).
 * Worked out fresh each time, so it's always right after reversals, price changes or a new order.
 * Plain Java on purpose — no database, easy to test.
 */
public final class PaymentAllocator {
    private PaymentAllocator() {}

    /** One thing charged. order = tie-breaker (e.g. when it was charged). */
    public record Line(int termKey, int priority, long order, Long groupId, String item, BigDecimal amount) {}

    /** paid.get(i) belongs to lines.get(i) (same order as given). */
    public record Result(List<BigDecimal> paid, BigDecimal credit) {}

    public static Result allocate(List<Line> lines, BigDecimal totalPaid) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) idx.add(i);
        idx.sort(Comparator.<Integer>comparingInt(i -> lines.get(i).termKey())
                .thenComparingInt(i -> lines.get(i).priority())
                .thenComparingLong(i -> lines.get(i).order())
                .thenComparingInt(i -> i));
        List<BigDecimal> paid = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) paid.add(BigDecimal.ZERO);
        BigDecimal left = totalPaid == null || totalPaid.signum() < 0 ? BigDecimal.ZERO : totalPaid;
        for (int i : idx) {
            if (left.signum() == 0) break;
            BigDecimal amt = lines.get(i).amount() == null ? BigDecimal.ZERO : lines.get(i).amount().max(BigDecimal.ZERO);
            BigDecimal take = left.min(amt);
            paid.set(i, take);
            left = left.subtract(take);
        }
        return new Result(paid, left);
    }
}
