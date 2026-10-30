package io.github.tjhgibbs.capella.core.cathedral;

/**
 * Every height in the section, in world Y, derived once from the arch solver and the plan.
 * A family of arches (arcade, aisle windows, clerestory) gets one springing and one crown from
 * its reference span, and every member is struck to them whatever its own span. Field meanings
 * are documented on Capella's {@code Levels} in {@code cathedral/plan.ts}.
 */
public final class Levels {
    public final double floorY;
    /** Raised sanctuary floor, east of the crossing. */
    public final double choirFloorY;
    public final double arcadeSpringY;
    public final double arcadeCrownY;
    public final double arcadeRingDepth;
    /** Top of the spandrel masonry; the triforium deck sits on it. */
    public final double spandrelTopY;
    /** Walking surface of the triforium gallery (or the leads over the aisle vault, two-storey). */
    public final double triforiumDeckY;
    public final double triforiumHeadY;
    public final boolean hasGallery;
    public final boolean hasMuralPassage;
    public final double muralFloorY;
    public final double muralHeadY;
    public final double clerestorySillY;
    public final double clerestorySpringY;
    public final double clerestoryCrownY;
    public final double vaultSpringY;
    public final double vaultCrownY;
    public final double vaultThickness;
    /** False under an open timber roof. */
    public final boolean highVault;
    public final double atticFloorY;
    public final double atticWalkY;
    public final double aisleVaultSpringY;
    public final double aisleVaultCrownY;
    /** The eaves. */
    public final double naveWallTopY;
    public final double roofRidgeY;
    public final double roofPitch;
    public final double aisleSillY;
    public final double aisleSpringY;
    public final double aisleCrownY;
    public final double aisleWallTopY;
    public final double crossingCrownY;
    public final double naveClearHeight;

    public Levels(double floorY, double choirFloorY, double arcadeSpringY, double arcadeCrownY, double arcadeRingDepth,
                  double spandrelTopY, double triforiumDeckY, double triforiumHeadY, boolean hasGallery,
                  boolean hasMuralPassage, double muralFloorY, double muralHeadY, double clerestorySillY,
                  double clerestorySpringY, double clerestoryCrownY, double vaultSpringY, double vaultCrownY,
                  double vaultThickness, boolean highVault, double atticFloorY, double atticWalkY,
                  double aisleVaultSpringY, double aisleVaultCrownY, double naveWallTopY, double roofRidgeY,
                  double roofPitch, double aisleSillY, double aisleSpringY, double aisleCrownY, double aisleWallTopY,
                  double crossingCrownY, double naveClearHeight) {
        this.floorY = floorY;
        this.choirFloorY = choirFloorY;
        this.arcadeSpringY = arcadeSpringY;
        this.arcadeCrownY = arcadeCrownY;
        this.arcadeRingDepth = arcadeRingDepth;
        this.spandrelTopY = spandrelTopY;
        this.triforiumDeckY = triforiumDeckY;
        this.triforiumHeadY = triforiumHeadY;
        this.hasGallery = hasGallery;
        this.hasMuralPassage = hasMuralPassage;
        this.muralFloorY = muralFloorY;
        this.muralHeadY = muralHeadY;
        this.clerestorySillY = clerestorySillY;
        this.clerestorySpringY = clerestorySpringY;
        this.clerestoryCrownY = clerestoryCrownY;
        this.vaultSpringY = vaultSpringY;
        this.vaultCrownY = vaultCrownY;
        this.vaultThickness = vaultThickness;
        this.highVault = highVault;
        this.atticFloorY = atticFloorY;
        this.atticWalkY = atticWalkY;
        this.aisleVaultSpringY = aisleVaultSpringY;
        this.aisleVaultCrownY = aisleVaultCrownY;
        this.naveWallTopY = naveWallTopY;
        this.roofRidgeY = roofRidgeY;
        this.roofPitch = roofPitch;
        this.aisleSillY = aisleSillY;
        this.aisleSpringY = aisleSpringY;
        this.aisleCrownY = aisleCrownY;
        this.aisleWallTopY = aisleWallTopY;
        this.crossingCrownY = crossingCrownY;
        this.naveClearHeight = naveClearHeight;
    }
}
