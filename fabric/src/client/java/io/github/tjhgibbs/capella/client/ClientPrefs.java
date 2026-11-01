package io.github.tjhgibbs.capella.client;

import io.github.tjhgibbs.capella.mod.CapellaMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Save-dialog choices that outlive the game session ({@code config/capella-client.properties}):
 * the formats last saved with and the placement Y offset. Written when either changes.
 */
final class ClientPrefs {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("capella-client.properties");

    static final Set<String> formats = new LinkedHashSet<>(List.of("axiom", "sponge", "14eredit"));

    static final int MAX_Y_OFFSET = 64;
    static int yOffset = 0;

    static {
        load();
    }

    private ClientPrefs() {
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(FILE)) {
            p.load(r);
            yOffset = Math.clamp(Integer.parseInt(p.getProperty("place.y_offset", "0").trim()), -MAX_Y_OFFSET, MAX_Y_OFFSET);
            String f = p.getProperty("save.formats");
            if (f != null) {
                formats.clear();
                Arrays.stream(f.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(formats::add);
            }
        } catch (IOException | RuntimeException e) {
            CapellaMod.LOG.warn("Could not read {}: {}", FILE, e.toString());
        }
    }

    static void save() {
        Properties p = new Properties();
        p.setProperty("place.y_offset", Integer.toString(yOffset));
        p.setProperty("save.formats", formats.stream().collect(Collectors.joining(",")));
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) {
                p.store(w, "Capella save dialog: the choices last saved with");
            }
        } catch (IOException e) {
            CapellaMod.LOG.warn("Could not write {}: {}", FILE, e.toString());
        }
    }
}
