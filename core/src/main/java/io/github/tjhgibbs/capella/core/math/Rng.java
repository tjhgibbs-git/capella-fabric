package io.github.tjhgibbs.capella.core.math;

import java.util.SplittableRandom;

/**
 * Deterministic random source. Every stage of generation forks its own stream from the tree seed
 * with a fixed salt, so changing (say) the leaf settings never reshuffles the branch layout.
 */
public final class Rng {
    private final SplittableRandom random;
    private final long seed;

    public Rng(long seed) {
        this.seed = seed;
        this.random = new SplittableRandom(mix(seed));
    }

    /** An independent stream derived from this one's seed and {@code salt}. */
    public Rng fork(String salt) {
        return new Rng(mix(seed ^ (salt.hashCode() * 0x9E3779B97F4A7C15L)));
    }

    public Rng fork(long salt) {
        return new Rng(mix(seed + salt * 0x632BE59BD9B4E019L));
    }

    public long seed() {
        return seed;
    }

    public double nextDouble() {
        return random.nextDouble();
    }

    public double range(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    /** {@code center} plus or minus up to {@code spread}. */
    public double around(double center, double spread) {
        return center + (random.nextDouble() * 2 - 1) * spread;
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public int range(int min, int maxInclusive) {
        return min + random.nextInt(maxInclusive - min + 1);
    }

    public boolean chance(double p) {
        return random.nextDouble() < p;
    }

    public double gaussian() {
        // Box-Muller; SplittableRandom has no nextGaussian on Java 17-.
        double u = 1.0 - random.nextDouble();
        double v = random.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2 * Math.PI * v);
    }


    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
