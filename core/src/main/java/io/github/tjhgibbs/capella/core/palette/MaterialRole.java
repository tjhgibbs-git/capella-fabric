package io.github.tjhgibbs.capella.core.palette;

/**
 * What an element is made of, as a role the palette answers (Capella {@code MATERIAL_ROLES}).
 * Ruin, greenery, clutter and debris roles are kept so palettes port whole; the generator in
 * this port emits only the building's own.
 */
public enum MaterialRole {
    MASONRY_MAIN, MASONRY_TRIM, MASONRY_FINE, RIB, TRACERY, FLOOR, FLOOR_WORN, ROOF, TIMBER,
    GLASS_A, GLASS_B, GLASS_C, GLASS_D, GLASS_E,
    RUBBLE_STONE, RUBBLE_TIMBER, RUBBLE_TILE,
    SOIL, TURF, ROCK, SUBSOIL, SAND,
    PLANT_IVY, PLANT_GROUND, PLANT_MOSS, PLANT_CANOPY, PLANT_TRUNK, PLANT_TRUNK_DEAD, PLANT_FLOATING,
    PLANT_REED, PLANT_ALGAE,
    CLUTTER_CRATE, CLUTTER_FURNITURE, STAGING, CHAIN, PORTCULLIS, LIGHT,
    DEBRIS_ROOF, DEBRIS_STONE, DEBRIS_ORGANIC, THATCH, INFILL;

    /**
     * The role a palette that has not answered this one is asked instead ({@code ROLE_FALLBACK});
     * null for a root every palette must answer itself. The fallback resolves the material pool,
     * not the draw: resolution still hashes on the role asked for.
     */
    public MaterialRole fallback() {
        return switch (this) {
            case MASONRY_MAIN, ROOF, TIMBER, GLASS_A, SOIL, ROCK, PLANT_IVY, PLANT_CANOPY, CLUTTER_CRATE, STAGING, CHAIN, LIGHT -> null;
            case MASONRY_TRIM, FLOOR, RUBBLE_STONE -> MASONRY_MAIN;
            case MASONRY_FINE -> MASONRY_TRIM;
            case RIB, TRACERY, INFILL, PORTCULLIS -> MASONRY_FINE;
            case FLOOR_WORN -> FLOOR;
            case THATCH, RUBBLE_TILE, DEBRIS_ROOF -> ROOF;
            case GLASS_B, GLASS_C, GLASS_D, GLASS_E -> GLASS_A;
            case RUBBLE_TIMBER, PLANT_TRUNK, CLUTTER_FURNITURE, DEBRIS_ORGANIC -> TIMBER;
            case TURF, SUBSOIL, SAND -> SOIL;
            case PLANT_GROUND, PLANT_MOSS -> PLANT_IVY;
            case PLANT_TRUNK_DEAD -> PLANT_TRUNK;
            case PLANT_FLOATING, PLANT_REED, PLANT_ALGAE -> PLANT_GROUND;
            case DEBRIS_STONE -> RUBBLE_STONE;
        };
    }
}
