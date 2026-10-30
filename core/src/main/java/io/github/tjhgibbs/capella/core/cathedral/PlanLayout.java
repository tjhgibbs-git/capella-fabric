package io.github.tjhgibbs.capella.core.cathedral;

import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.kit.PlacementFrame;
import io.github.tjhgibbs.capella.core.layout.Portal;

import java.util.List;

/**
 * A resolved plan (Capella {@code PlanLayout}). Everything here is in the plan's own frame,
 * with the nave along +X; {@link #frame} turns it into the world. Capella's {@code crypt},
 * {@code platforms} and {@code mezzanines} are not ported: the floor is level per bay.
 */
public final class PlanLayout {
    public final String id;
    /** Always "cathedral". */
    public final String type;
    public final PlacementFrame frame;
    public final PlanParams plan;
    /** Inner face of the west front. */
    public final double westX;
    /** East end of the last nave bay, before any chevet. */
    public final double eastX;
    /** Nave centreline. */
    public final double axisZ;
    public final List<Bay> bays;
    /** West to east; {@code crossings[0]} is the principal. */
    public final List<Crossing> crossings;
    /** Nave interior, half-open. */
    public final double naveZ0;
    public final double naveZ1;
    public final SideBands north;
    public final SideBands south;
    public final Levels levels;
    public final double wallThickness;
    /** Thickness of the arcade band, and so the depth of a pier. */
    public final double arcadeDepth;
    public final double pierWidth;
    public final double referenceSpan;
    /** Depth of the chevet east of {@code eastX}; 0 for a flat east end. */
    public final double chevetDepth;
    public final double maxTranseptReach;
    public final double towerSize;
    public final List<Portal> portals;
    public final Box footprint;
    public final double naveHalf;
    public final double outerHalf;
    public final double naveHeight;
    public final double aisleHeight;
    public final double floorY;

    PlanLayout(String id, String type, PlacementFrame frame, PlanParams plan, double westX, double eastX, double axisZ,
               List<Bay> bays, List<Crossing> crossings, double naveZ0, double naveZ1, SideBands north,
               SideBands south, Levels levels, double wallThickness, double arcadeDepth, double pierWidth,
               double referenceSpan, double chevetDepth, double maxTranseptReach, double towerSize,
               List<Portal> portals, Box footprint, double naveHalf, double outerHalf, double naveHeight,
               double aisleHeight, double floorY) {
        this.id = id;
        this.type = type;
        this.frame = frame;
        this.plan = plan;
        this.westX = westX;
        this.eastX = eastX;
        this.axisZ = axisZ;
        this.bays = bays;
        this.crossings = crossings;
        this.naveZ0 = naveZ0;
        this.naveZ1 = naveZ1;
        this.north = north;
        this.south = south;
        this.levels = levels;
        this.wallThickness = wallThickness;
        this.arcadeDepth = arcadeDepth;
        this.pierWidth = pierWidth;
        this.referenceSpan = referenceSpan;
        this.chevetDepth = chevetDepth;
        this.maxTranseptReach = maxTranseptReach;
        this.towerSize = towerSize;
        this.portals = portals;
        this.footprint = footprint;
        this.naveHalf = naveHalf;
        this.outerHalf = outerHalf;
        this.naveHeight = naveHeight;
        this.aisleHeight = aisleHeight;
        this.floorY = floorY;
    }
}
