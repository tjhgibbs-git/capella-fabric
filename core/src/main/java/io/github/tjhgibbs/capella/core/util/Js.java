package io.github.tjhgibbs.capella.core.util;

/**
 * JavaScript number semantics the port has to reproduce exactly, because element ids and hash
 * keys are built from them.
 */
public final class Js {
    private Js() {
    }

    /** {@code String(n)} for the numbers the generator uses: integers print without ".0". */
    public static String str(double n) {
        if (n == Math.rint(n) && !Double.isInfinite(n) && Math.abs(n) < 1e21) {
            return Long.toString((long) n);
        }
        if (Double.isNaN(n)) return "NaN";
        if (Double.isInfinite(n)) return n > 0 ? "Infinity" : "-Infinity";
        // Shortest round-trip repr, as JS does; Java's Double.toString is also shortest-repr
        // (JDK 19+) but uses E notation from 1e7 and below 1e-3 where JS does not.
        String s = Double.toString(n);
        if (s.contains("E")) s = new java.math.BigDecimal(s).toPlainString();
        return s;
    }

    /** {@code Math.round}: half rounds towards +infinity. Returns a double, as JS does. */
    public static double round(double x) {
        return Math.floor(x + 0.5);
    }

    /** {@code Math.sign}. */
    public static double sign(double x) {
        return Math.signum(x);
    }

    /** True when {@code x} is a finite integer ({@code Number.isInteger}). */
    public static boolean isInteger(double x) {
        return !Double.isInfinite(x) && x == Math.rint(x);
    }
}
