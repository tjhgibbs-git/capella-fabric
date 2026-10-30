package io.github.tjhgibbs.capella.core.raster;

/**
 * What the collapse cascade left standing, as the rasterizer reads it (Capella's
 * {@code CollapseMask}). Not produced by this port yet: the hook stays so ruin can come back
 * without touching the rasterizer.
 */
public interface RuinMask {
    /** The element fell entirely. */
    boolean gone(String elementId);

    /** The element survives only up to {@link Surface#topAt}, or null if it stands whole. */
    Surface surface(String elementId);

    interface Surface {
        double topAt(int x, int z);
    }
}
