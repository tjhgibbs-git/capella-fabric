package io.github.tjhgibbs.capella.core.export;

import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Writes a volume to disk in a given format, atomically, with a sanitised file name. */
public final class Exporter {
    private Exporter() {
    }

    /** Lower-case letters, digits, '_' and '-' only; never empty. */
    public static String safeName(String name) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_");
        s = s.replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "tree" : s.length() > 64 ? s.substring(0, 64) : s;
    }

    /** Writes {@code dir/<safe name><ext>} and returns the path. */
    public static Path write(SchematicFormat format, BlockVolume volume, ExportMeta meta, Path dir) throws IOException {
        Files.createDirectories(dir);
        Path target = dir.resolve(safeName(meta.name()) + format.extension());
        Path tmp = Files.createTempFile(dir, ".capella", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tmp)) {
                format.write(volume, meta, out);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
        return target;
    }
}
