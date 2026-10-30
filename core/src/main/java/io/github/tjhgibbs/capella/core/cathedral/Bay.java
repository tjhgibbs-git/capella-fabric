package io.github.tjhgibbs.capella.core.cathedral;

import java.util.Locale;

/** One structural repeat of the nave, between two pier lines. */
public final class Bay {
    public enum Kind {
        TOWER, NAVE, CROSSING, CHOIR;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** 1-based, matching {@code TranseptParams.atBay}. */
    public final int index;
    /** West boundary, where the piers stand. */
    public final double x0;
    /** East boundary, exclusive. */
    public final double x1;
    public final double span;
    public final Kind kind;
    public final boolean isCrossing;
    /**
     * On the raised sanctuary floor: everything east of the principal crossing. Not the same as
     * {@code kind == CHOIR}: an eastern crossing is raised but is a crossing bay.
     */
    public final boolean raised;

    public Bay(int index, double x0, double x1, double span, Kind kind, boolean isCrossing, boolean raised) {
        this.index = index;
        this.x0 = x0;
        this.x1 = x1;
        this.span = span;
        this.kind = kind;
        this.isCrossing = isCrossing;
        this.raised = raised;
    }
}
