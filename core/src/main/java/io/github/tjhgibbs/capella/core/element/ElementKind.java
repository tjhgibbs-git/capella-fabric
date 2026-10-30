package io.github.tjhgibbs.capella.core.element;

import java.util.Locale;

/** Capella's {@code ElementKind}. Only the kinds a pristine cathedral emits are kept. */
public enum ElementKind {
    MASSING, PLINTH, FLOOR, TERRAIN, PIER, ARCH, WALL, APERTURE, TRACERY, RIB, VAULT_WEB, BUTTRESS, FLYER,
    PINNACLE, ROOF, TIMBER, STAIR, TOWER, SPIRE, CRENELLATION, RAMPART,
    /** Window glass: fills only the air an aperture left, so mullions and tracery stay. Not in Capella. */
    GLAZING,
    /** Furnishings and light fittings: likewise fill only air. Not in Capella. */
    FIXTURE;

    /** The TypeScript id: lower case, '-' for '_' ({@code vault-web}). */
    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
