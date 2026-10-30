package io.github.tjhgibbs.capella.core.element;

import java.util.Locale;

/** Capella's {@code ElementKind}. Only the kinds a pristine cathedral emits are kept. */
public enum ElementKind {
    MASSING, PLINTH, FLOOR, TERRAIN, PIER, ARCH, WALL, APERTURE, TRACERY, RIB, VAULT_WEB, BUTTRESS, FLYER,
    PINNACLE, ROOF, TIMBER, STAIR, TOWER, SPIRE, CRENELLATION, RAMPART;

    /** The TypeScript id: lower case, '-' for '_' ({@code vault-web}). */
    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
