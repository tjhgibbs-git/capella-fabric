package io.github.tjhgibbs.capella.core.cathedral;

/**
 * A rough statics model that sizes the buttresses (Capella {@code thrust.ts}): a vault's
 * horizontal thrust, a buttress's resistance to overturning, and the depth and pinnacle weight
 * that make up the difference. Not engineering; it makes bigger vaults get bigger buttresses.
 */
final class Thrust {
    static final double MIN_BUTTRESS_DEPTH = 2;
    static final double MAX_BUTTRESS_DEPTH = 10;
    static final double PILASTER_DEPTH = 2;

    private Thrust() {
    }

    record Force(double h, double v) {
    }

    static Force vaultThrust(double span, double length, double rise, double thickness) {
        double weight = span * length * thickness;
        double v = weight / 2;
        return new Force((v * span) / (8 * Math.max(1, rise)), v);
    }

    static double overturningCapacity(double depth, double width, double wallThickness, double height) {
        double wall = wallThickness * height * width * (depth + wallThickness / 2);
        double mass = depth * height * width * (depth / 2);
        return wall + mass;
    }

    static double overturningDemand(double h, double arm) {
        return h * Math.max(0, arm);
    }

    static double requiredDepth(double demand, double width, double wallThickness, double height) {
        for (double depth = MIN_BUTTRESS_DEPTH; depth < MAX_BUTTRESS_DEPTH; depth++) {
            if (overturningCapacity(depth, width, wallThickness, height) >= demand) return depth;
        }
        return MAX_BUTTRESS_DEPTH;
    }

    /** "pilaster" or "pier". */
    static String formOfDepth(double depth) {
        return depth <= PILASTER_DEPTH ? "pilaster" : "pier";
    }

    static double pinnacleHeightFor(double shortfall, double footprint, double depth) {
        if (shortfall <= 0) return 0;
        double mass = shortfall / Math.max(1, depth / 2);
        return Math.max(6, Math.min(18, Math.round(mass / Math.max(1, footprint))));
    }
}
