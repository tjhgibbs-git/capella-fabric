package io.github.tjhgibbs.capella.core.export;

import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 14erEdit's "SchemLite" text format (com._14ercooper.schematics.SchemLite), read by
 * {@code /fx schem load <name>} from {@code config/14erEdit/schematics/}:
 *
 * <pre>
 *   xSize \n ySize \n zSize \n author \n date \n
 *   then per block, x fastest, then z, then y (CubeIterator order 0), from the min corner:
 *   MATERIAL \n blockdata \n nbt \n
 * </pre>
 *
 * MATERIAL is the Bukkit-style name (OAK_LOG), blockdata the full state string, nbt empty.
 * 14erEdit pastes with the player at the minimum corner; Capella reports the offset to use.
 */
public final class FourteenerSchemLite implements SchematicFormat {
    @Override
    public String id() {
        return "14eredit";
    }

    @Override
    public String extension() {
        return "";
    }

    @Override
    public void write(BlockVolume v, ExportMeta meta, OutputStream out) throws IOException {
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        w.write(v.width + "\n" + v.height + "\n" + v.length + "\n");
        w.write(oneLine(meta.author()) + "\n");
        w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(meta.createdMillis())) + "\n");
        String[] material = new String[v.palette().size()];
        for (int i = 0; i < material.length; i++) {
            material[i] = materialName(v.palette().get(i));
        }
        for (int y = 0; y < v.height; y++) {
            for (int z = 0; z < v.length; z++) {
                for (int x = 0; x < v.width; x++) {
                    int id = v.idAt(x, y, z);
                    w.write(material[id]);
                    w.write('\n');
                    w.write(v.palette().get(id));
                    w.write("\n\n");
                }
            }
        }
        w.flush();
    }

    /** {@code minecraft:oak_log[axis=y]} -> {@code OAK_LOG}; other namespaces keep theirs. */
    static String materialName(String state) {
        String id = BlockStateString.parse(state).name();
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        return id.toUpperCase(Locale.ROOT);
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ');
    }
}
