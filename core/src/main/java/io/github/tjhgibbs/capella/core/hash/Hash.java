package io.github.tjhgibbs.capella.core.hash;

import java.util.List;

/**
 * Port of Capella's {@code hash.ts}: FNV-1a over typed keys with a murmur finaliser. Every
 * variation in the generator comes from {@code hash(seed, key...)}, so this must agree with the
 * TypeScript bit for bit; the oracle comparison in the tests depends on it.
 *
 * <p>Keys are {@link String}s or {@link Number}s. A number that is an integer in int32 range is
 * mixed as four bytes; any other number as its IEEE-754 bits (as JavaScript would see it).
 */
public final class Hash {
    static final int FNV_OFFSET = 0x811c9dc5;
    static final int FNV_PRIME = 0x01000193;
    private static final double TO_UNIT = 2.3283064365386963e-10;

    private Hash() {
    }

    static int mixByte(int h, int b) {
        return (h ^ (b & 0xff)) * FNV_PRIME;
    }

    static int mixUint32(int h, int v) {
        int acc = (h ^ (v & 0xff)) * FNV_PRIME;
        acc = (acc ^ ((v >>> 8) & 0xff)) * FNV_PRIME;
        acc = (acc ^ ((v >>> 16) & 0xff)) * FNV_PRIME;
        acc = (acc ^ ((v >>> 24) & 0xff)) * FNV_PRIME;
        return acc;
    }

    static int mixNumber(int h, double v) {
        if (v == Math.rint(v) && v >= -2147483648.0 && v <= 2147483647.0) {
            return mixUint32(h ^ 0x01, (int) v);
        }
        long bits = Double.doubleToLongBits(v);
        int acc = mixUint32(h ^ 0x02, (int) bits);
        return mixUint32(acc, (int) (bits >>> 32));
    }

    static int mixString(int h, String s) {
        int acc = h ^ 0x03;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            acc = mixByte(acc, c);
            acc = mixByte(acc, c >>> 8);
        }
        return mixUint32(acc, s.length());
    }

    static int mixKey(int h, Object k) {
        if (k instanceof String s) return mixString(h, s);
        if (k instanceof Number n) return mixNumber(h, n.doubleValue());
        throw new IllegalArgumentException("hash key must be a String or Number: " + k);
    }

    static int avalanche(int x) {
        x ^= x >>> 16;
        x *= 0x85ebca6b;
        x ^= x >>> 13;
        x *= 0xc2b2ae35;
        x ^= x >>> 16;
        return x;
    }

    /** Unsigned 32-bit hash, as a non-negative long. */
    public static long hashU32(double seed, Object... keys) {
        int h = mixNumber(FNV_OFFSET, seed);
        for (Object k : keys) h = mixKey(h, k);
        return avalanche(h) & 0xffffffffL;
    }

    /** The mixed state after {@code seed} and one string key, before the finaliser. */
    public static int hashPrefix(double seed, String key) {
        return mixString(mixNumber(FNV_OFFSET, seed), key);
    }

    /** Uniform in [0, 1). */
    public static double hash(double seed, Object... keys) {
        return hashU32(seed, keys) * TO_UNIT;
    }

    /** Integer in [lo, hi], inclusive. */
    public static int hashInt(int lo, int hi, double seed, Object... keys) {
        if (hi < lo) throw new IllegalArgumentException("hashInt: empty range [" + lo + ", " + hi + "]");
        int span = hi - lo + 1;
        return lo + (int) Math.floor(hash(seed, keys) * span);
    }

    public static double hashRange(double lo, double hi, double seed, Object... keys) {
        return lo + hash(seed, keys) * (hi - lo);
    }

    public static boolean hashChance(double p, double seed, Object... keys) {
        return hash(seed, keys) < p;
    }

    public static <T> T hashPick(List<T> items, double seed, Object... keys) {
        if (items.isEmpty()) throw new IllegalArgumentException("hashPick: empty list");
        int i = Math.min(items.size() - 1, (int) Math.floor(hash(seed, keys) * items.size()));
        return items.get(i);
    }

    public static <T> T hashWeighted(List<T> items, double[] weights, double seed, Object... keys) {
        return pickWeightedRaw(hash(seed, keys), items, weights, false);
    }

    /** {@code pickWeighted}: choose by an already-drawn number in [0, 1). */
    public static <T> T pickWeighted(double draw, List<T> items, double[] weights) {
        return pickWeightedRaw(Math.min(0.999999, Math.max(0, draw)), items, weights, true);
    }

    private static <T> T pickWeightedRaw(double draw, List<T> items, double[] weights, boolean clamped) {
        if (items.isEmpty()) throw new IllegalArgumentException("weighted pick: empty list");
        if (items.size() != weights.length) throw new IllegalArgumentException("weighted pick: length mismatch");
        double total = 0;
        for (double w : weights) {
            if (!(w >= 0)) throw new IllegalArgumentException("weighted pick: bad weight " + w);
            total += w;
        }
        if (total <= 0) throw new IllegalArgumentException("weighted pick: weights sum to zero");
        double r = draw * total;
        for (int i = 0; i < items.size(); i++) {
            r -= weights[i];
            if (r < 0) return items.get(i);
        }
        return items.get(items.size() - 1);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    public static double noise2(double seed, String key, double x, double z, double wavelength) {
        return noise2p(hashPrefix(seed, key), x, z, wavelength);
    }

    public static double noise2p(int prefix, double x, double z, double wavelength) {
        double w = wavelength > 0 ? wavelength : 1;
        double fx = x / w, fz = z / w;
        double x0 = Math.floor(fx), z0 = Math.floor(fz);
        double tx = smooth(fx - x0), tz = smooth(fz - z0);
        return plane(mixNumber(prefix, x0), mixNumber(prefix, x0 + 1), z0, tx, tz);
    }

    public static double noise3(double seed, String key, double x, double y, double z, double wavelength) {
        return noise3p(hashPrefix(seed, key), x, y, z, wavelength);
    }

    public static double noise3p(int prefix, double x, double y, double z, double wavelength) {
        double w = wavelength > 0 ? wavelength : 1;
        double fx = x / w, fy = y / w, fz = z / w;
        double x0 = Math.floor(fx), y0 = Math.floor(fy), z0 = Math.floor(fz);
        double tx = smooth(fx - x0), ty = smooth(fy - y0), tz = smooth(fz - z0);
        int hx0 = mixNumber(prefix, x0), hx1 = mixNumber(prefix, x0 + 1);
        int h00 = mixNumber(hx0, y0), h10 = mixNumber(hx1, y0);
        int h01 = mixNumber(hx0, y0 + 1), h11 = mixNumber(hx1, y0 + 1);
        double lo = plane(h00, h10, z0, tx, tz);
        double hi = plane(h01, h11, z0, tx, tz);
        return lo + (hi - lo) * ty;
    }

    private static double plane(int hx0, int hx1, double z0, double tx, double tz) {
        double v00 = tail(hx0, z0), v10 = tail(hx1, z0), v01 = tail(hx0, z0 + 1), v11 = tail(hx1, z0 + 1);
        double a = v00 + (v10 - v00) * tx;
        double b = v01 + (v11 - v01) * tx;
        return a + (b - a) * tz;
    }

    private static double tail(int h, double v) {
        return (avalanche(mixNumber(h, v)) & 0xffffffffL) * TO_UNIT;
    }

    public static long hashBytes(byte[] bytes) {
        int acc = FNV_OFFSET;
        for (byte b : bytes) acc = mixByte(acc, b);
        return avalanche(acc) & 0xffffffffL;
    }

    public static String toHex(long h) {
        return String.format("%08x", h & 0xffffffffL);
    }
}
