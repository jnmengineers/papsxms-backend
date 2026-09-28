package jnm.engineer.demo.security;

import java.security.SecureRandom;

/**
 * Random temporary passwords, e.g. "Kp7mQx3Rwa".
 * Leaves out look-alike characters (0/O, 1/l/I) so they're easy to read out and type.
 */
public final class PasswordGenerator {
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALL = UPPER + LOWER + DIGITS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {}

    /** 10 characters with at least one capital, one small letter and one number. */
    public static String temporary() {
        char[] p = new char[10];
        p[0] = UPPER.charAt(RANDOM.nextInt(UPPER.length()));
        p[1] = LOWER.charAt(RANDOM.nextInt(LOWER.length()));
        p[2] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
        for (int i = 3; i < p.length; i++) p[i] = ALL.charAt(RANDOM.nextInt(ALL.length()));
        for (int i = p.length - 1; i > 0; i--) {                 // shuffle
            int j = RANDOM.nextInt(i + 1);
            char t = p[i]; p[i] = p[j]; p[j] = t;
        }
        return new String(p);
    }
}
