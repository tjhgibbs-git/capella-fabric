package io.github.tjhgibbs.capella.core.cathedral;

/**
 * A crossing and its two arms. {@code tag} is "" for the principal crossing so its elements keep
 * their historical ids ({@code wall/transept-north-west}); later ones are "2", "3"...
 */
public final class Crossing {
    /** 0-based, west to east. 0 is the principal crossing. */
    public final int at;
    public final Bay bay;
    public final String tag;
    public final int armBays;
    /** Axis to the outside of an arm's end wall. */
    public final double reach;

    public Crossing(int at, Bay bay, String tag, int armBays, double reach) {
        this.at = at;
        this.bay = bay;
        this.tag = tag;
        this.armBays = armBays;
        this.reach = reach;
    }

    /** {@code armName}: {@code transept-north}, {@code transept2-south}. {@code side} is "north" or "south". */
    public static String armName(Crossing crossing, String side) {
        return "transept" + crossing.tag + "-" + side;
    }
}
