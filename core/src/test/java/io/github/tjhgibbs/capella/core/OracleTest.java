package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.cathedral.CathedralPlan;
import io.github.tjhgibbs.capella.core.cathedral.CathedralShell;
import io.github.tjhgibbs.capella.core.cathedral.PlanLayout;
import io.github.tjhgibbs.capella.core.cathedral.Scene;
import io.github.tjhgibbs.capella.core.debug.ElementJson;
import io.github.tjhgibbs.capella.core.element.ElementTree;
import io.github.tjhgibbs.capella.core.palette.Palettes;
import io.github.tjhgibbs.capella.core.raster.Normalize;
import io.github.tjhgibbs.capella.core.raster.Rasterizer;
import io.github.tjhgibbs.capella.core.raster.Volume;
import io.github.tjhgibbs.capella.core.util.Json;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The port against Capella's own output. {@code tools/oracle/dump.mts} ran the TypeScript on each
 * fixture with the footing dropped and wrote, under {@code resources/oracle/}, the reduced scene,
 * every element, and a digest of every block after rasterize and normalize. Both must match
 * exactly: same element ids, kinds, roles, geometry and params, and the same voxels.
 *
 * On a block mismatch, regenerate {@code <name>.blocks.txt} with dump.mts, write the Java side's
 * with {@link #blockLines} and diff them with {@code tools/oracle/compare.py blocks}.
 */
class OracleTest {
    @ParameterizedTest
    @ValueSource(strings = {"parish", "minimal", "default", "irregular-bays", "max-plan", "double-transept", "turned"})
    void matchesTheTypeScript(String name) throws Exception {
        Scene scene = Scene.fromJson(text(name + ".scene.json", false));
        PlanLayout layout = CathedralPlan.resolveLayout(scene);
        ElementTree tree = CathedralShell.generateShell(scene, layout, "");

        List<String> diffs = new ArrayList<>();
        Map<String, Object> ts = byId(Json.parse(text(name + ".elements.json.gz", true)));
        Map<String, Object> java = byId(Json.parse(ElementJson.write(tree)));
        for (String id : ts.keySet()) if (!java.containsKey(id)) diffs.add("only ts: " + id);
        for (String id : java.keySet()) if (!ts.containsKey(id)) diffs.add("only java: " + id);
        for (Map.Entry<String, Object> e : ts.entrySet()) {
            if (java.containsKey(e.getKey())) same(e.getValue(), java.get(e.getKey()), e.getKey(), diffs);
        }
        if (!diffs.isEmpty()) {
            fail(name + ": " + diffs.size() + " element differences, first:\n  "
                    + String.join("\n  ", diffs.subList(0, Math.min(20, diffs.size()))));
        }

        Volume volume = Rasterizer.rasterize(scene.boundsX, scene.boundsZ, scene.seed, tree,
                new Rasterizer.Options(Palettes.get(scene.palette))).volume();
        Normalize.normalizeVolume(volume);
        List<String> lines = blockLines(volume);
        String blocks = String.join("\n", lines) + "\n";
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(blocks.getBytes(StandardCharsets.UTF_8)));
        assertEquals(text(name + ".oracle.txt", false), "blocks " + lines.size() + "\nsha256 " + sha + "\n", name + " blocks");
    }

    /** Every non-air voxel as "x y z state", in dump.mts's order. */
    static List<String> blockLines(Volume v) {
        List<String> out = new ArrayList<>();
        for (int y = v.minY; y < v.minY + v.sizeY; y++)
            for (int z = v.minZ; z < v.minZ + v.sizeZ; z++)
                for (int x = v.minX; x < v.minX + v.sizeX; x++) {
                    String k = v.keyAt(x, y, z);
                    if (!k.equals("minecraft:air")) out.add(x + " " + y + " " + z + " " + k);
                }
        return out;
    }

    private static Map<String, Object> byId(Object doc) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Object e : Json.array(Json.object(doc).get("elements"))) out.put((String) Json.object(e).get("id"), e);
        return out;
    }

    private static void same(Object a, Object b, String path, List<String> out) {
        if (a instanceof Number x && b instanceof Number y) {
            if (Math.abs(x.doubleValue() - y.doubleValue()) > 1e-9) out.add(path + ": " + a + " != " + b);
        } else if (a instanceof Map<?, ?> x && b instanceof Map<?, ?> y) {
            for (Object k : x.keySet()) {
                if (!y.containsKey(k)) out.add(path + "." + k + ": missing on java side");
                else same(x.get(k), y.get(k), path + "." + k, out);
            }
            for (Object k : y.keySet()) if (!x.containsKey(k)) out.add(path + "." + k + ": missing on ts side");
        } else if (a instanceof List<?> x && b instanceof List<?> y) {
            if (x.size() != y.size()) {
                out.add(path + ": length " + x.size() + " != " + y.size());
                return;
            }
            for (int i = 0; i < x.size(); i++) same(x.get(i), y.get(i), path + "[" + i + "]", out);
        } else if (a == null ? b != null : !a.equals(b)) {
            out.add(path + ": " + a + " != " + b);
        }
    }

    private static String text(String resource, boolean gzip) throws IOException {
        InputStream in = OracleTest.class.getResourceAsStream("/oracle/" + resource);
        assertTrue(in != null, "missing oracle resource " + resource);
        try (InputStream s = gzip ? new GZIPInputStream(in) : in) {
            return new String(s.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
