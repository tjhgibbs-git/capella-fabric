package io.github.tjhgibbs.capella.mod;

import io.github.tjhgibbs.capella.core.export.SchematicFormat;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Where each format is saved, chosen so the target tool finds the file without copying:
 * <ul>
 *   <li>axiom (.bp): {@code config/axiom/blueprints/Capella}, in Axiom’s blueprint browser;</li>
 *   <li>sponge (.schem): WorldEdit's folder when WorldEdit is installed, else
 *       {@code schematics/} in the game dir (Axiom imports .schem from there via its file picker
 *       or drag-and-drop);</li>
 *   <li>14eredit: {@code config/14erEdit/schematics}, where {@code /fx schem load} reads;</li>
 *   <li>structure (.nbt): {@code schematics/structures}; copy into a world's
 *       {@code generated/<ns>/structure} to use {@code /place template}.</li>
 * </ul>
 */
public final class ExportPaths {
    private ExportPaths() {
    }

    public static Path dirFor(SchematicFormat f) {
        FabricLoader fl = FabricLoader.getInstance();
        return switch (f.id()) {
            case "axiom" -> fl.getConfigDir().resolve("axiom").resolve("blueprints").resolve("Capella");
            case "14eredit" -> fl.getConfigDir().resolve("14erEdit").resolve("schematics");
            case "structure" -> fl.getGameDir().resolve("schematics").resolve("structures");
            default -> fl.isModLoaded("worldedit")
                    ? fl.getConfigDir().resolve("worldedit").resolve("schematics")
                    : fl.getGameDir().resolve("schematics");
        };
    }
}
