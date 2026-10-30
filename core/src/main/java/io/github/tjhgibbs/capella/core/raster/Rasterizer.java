package io.github.tjhgibbs.capella.core.raster;

import io.github.tjhgibbs.capella.core.blockstate.BlockState;
import io.github.tjhgibbs.capella.core.blockstate.Blockstates;
import io.github.tjhgibbs.capella.core.element.Element;
import io.github.tjhgibbs.capella.core.element.ElementKind;
import io.github.tjhgibbs.capella.core.element.ElementTree;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.kit.Geometry;
import io.github.tjhgibbs.capella.core.palette.BlockForm;
import io.github.tjhgibbs.capella.core.palette.Palette;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Elements to voxels (Capella {@code raster/rasterize.ts}). Elements are written in a fixed order
 * — pass, then kind, then id — so the result never depends on the order they were built in.
 * An aperture writes air; everything else asks the palette for its role at each voxel.
 */
public final class Rasterizer {
    private Rasterizer() {
    }

    private static final Map<ElementKind, RasterPass> PASS_OF_KIND = new EnumMap<>(ElementKind.class);
    private static final Map<ElementKind, Integer> KIND_ORDER = new EnumMap<>(ElementKind.class);

    private static void kind(ElementKind k, RasterPass pass, int order) {
        PASS_OF_KIND.put(k, pass);
        KIND_ORDER.put(k, order);
    }

    static {
        kind(ElementKind.TERRAIN, RasterPass.STRUCTURE, -1);
        kind(ElementKind.MASSING, RasterPass.STRUCTURE, 1);
        kind(ElementKind.TOWER, RasterPass.STRUCTURE, 1);
        kind(ElementKind.WALL, RasterPass.STRUCTURE, 2);
        kind(ElementKind.PIER, RasterPass.STRUCTURE, 3);
        kind(ElementKind.BUTTRESS, RasterPass.STRUCTURE, 3);
        kind(ElementKind.FLYER, RasterPass.STRUCTURE, 4);
        kind(ElementKind.ARCH, RasterPass.STRUCTURE, 5);
        kind(ElementKind.RIB, RasterPass.STRUCTURE, 6);
        kind(ElementKind.VAULT_WEB, RasterPass.STRUCTURE, 7);
        kind(ElementKind.ROOF, RasterPass.STRUCTURE, 8);
        kind(ElementKind.SPIRE, RasterPass.STRUCTURE, 8);
        kind(ElementKind.TIMBER, RasterPass.STRUCTURE, 9);
        kind(ElementKind.PINNACLE, RasterPass.STRUCTURE, 9);
        kind(ElementKind.CRENELLATION, RasterPass.STRUCTURE, 9);
        kind(ElementKind.RAMPART, RasterPass.STRUCTURE, 9);
        kind(ElementKind.STAIR, RasterPass.STRUCTURE, 10);
        kind(ElementKind.TRACERY, RasterPass.DECORATION, 11);
        kind(ElementKind.PLINTH, RasterPass.FLOORS, 0);
        kind(ElementKind.FLOOR, RasterPass.FLOORS, 12);
        kind(ElementKind.APERTURE, RasterPass.APERTURES, 13);
        for (ElementKind k : ElementKind.values()) {
            if (!PASS_OF_KIND.containsKey(k)) throw new IllegalStateException("no raster pass for " + k);
        }
    }

    public static RasterPass passOf(ElementKind kind) {
        return PASS_OF_KIND.get(kind);
    }

    public record Result(Volume volume, int clipped) {
    }

    /**
     * Options. {@code into} reuses a volume (else one is sized by {@link #buildBounds});
     * {@code onlyElements} restricts the write; {@code ruin} and {@code weathering} may be null.
     */
    public static final class Options {
        public final Palette palette;
        public Set<String> onlyElements;
        public Volume into;
        public RuinMask ruin;
        public ColumnField weathering;

        public Options(Palette palette) {
            this.palette = palette;
        }
    }

    /**
     * The volume a build is written into: the elements' bounds, clipped in x and z to the scene's
     * [0, boundsX) x [0, boundsZ). A one-block box when nothing is left.
     */
    public static Box buildBounds(double sceneBoundsX, double sceneBoundsZ, ElementTree tree) {
        Box treeBounds = tree.bounds();
        if (treeBounds == null) return Box.of(0, 0, 0, 1, 1, 1);
        Box clip = Box.of(0, treeBounds.min().y(), 0, sceneBoundsX, treeBounds.max().y(), sceneBoundsZ);
        Box result = treeBounds.intersection(clip);
        return result.isEmpty() ? Box.of(0, 0, 0, 1, 1, 1) : result;
    }

    public static Result rasterize(double sceneBoundsX, double sceneBoundsZ, double seed, ElementTree tree, Options options) {
        Volume volume = options.into != null ? options.into : new Volume(buildBounds(sceneBoundsX, sceneBoundsZ, tree));
        ColumnField weathering = options.weathering != null ? options.weathering : ColumnField.ZERO;
        int[] clipped = {0};

        List<Element> ordered = new ArrayList<>(tree.elements());
        ordered.sort((a, b) -> {
            int c = Integer.compare(PASS_OF_KIND.get(a.kind()).ordinal(), PASS_OF_KIND.get(b.kind()).ordinal());
            if (c != 0) return c;
            c = Integer.compare(KIND_ORDER.get(a.kind()), KIND_ORDER.get(b.kind()));
            if (c != 0) return c;
            return a.id().compareTo(b.id());
        });

        Map<String, Map<String, Integer>> idByProps = new HashMap<>();
        for (Element element : ordered) {
            if (options.onlyElements != null && !options.onlyElements.contains(element.id())) continue;
            if (options.ruin != null && options.ruin.gone(element.id())) continue;
            RuinMask.Surface ruin = options.ruin != null ? options.ruin.surface(element.id()) : null;
            RasterPass pass = PASS_OF_KIND.get(element.kind());
            BlockForm form = formOf(element);
            Map<String, String> props = orientationOf(element, form);
            StringBuilder pk = new StringBuilder();
            for (Map.Entry<String, String> e : props.entrySet()) {
                if (pk.length() > 0) pk.append(',');
                pk.append(e.getKey()).append('=').append(e.getValue());
            }
            Map<String, Integer> names = idByProps.computeIfAbsent(pk.toString(), k -> new HashMap<>());
            Palette.RoleResolver resolve = options.palette.roleResolver(element.role(), form, seed);

            boolean byColumn = ruin != null && element.geometry() instanceof Geometry.HeightField h
                    && h.mode() == Geometry.HeightMode.SHELL;
            int[] column = {Integer.MIN_VALUE, Integer.MIN_VALUE};
            boolean[] columnStands = {true};
            int[] sample = {Integer.MIN_VALUE, Integer.MIN_VALUE};
            double[] sampleW = {0};

            Primitives.rasterizeGeometry(element.geometry(), (x, y, z) -> {
                if (ruin != null) {
                    if (byColumn) {
                        if (x != column[0] || z != column[1]) {
                            column[0] = x;
                            column[1] = z;
                            columnStands[0] = y <= ruin.topAt(x, z);
                        }
                        if (!columnStands[0]) return;
                    } else if (y > ruin.topAt(x, z)) {
                        return;
                    }
                }
                if (!volume.contains(x, y, z)) {
                    clipped[0]++;
                    return;
                }
                if (pass == RasterPass.APERTURES) {
                    volume.setId(x, y, z, 0, pass);
                    return;
                }
                if (x != sample[0] || z != sample[1]) {
                    sample[0] = x;
                    sample[1] = z;
                    sampleW[0] = clampByte(weathering.sample(x + 0.5, z + 0.5));
                }
                String name = resolve.resolve(x, y, z, sampleW[0]);
                Integer id = names.get(name);
                if (id == null) id = internInto(volume, names, name, props);
                volume.setId(x, y, z, id, pass);
            });
        }
        return new Result(volume, clipped[0]);
    }

    /** Applies only the orientation props the block actually has (stairs take facing/half, etc.). */
    private static int internInto(Volume volume, Map<String, Integer> into, String name, Map<String, String> props) {
        BlockState base = Blockstates.makeState(name);
        Map<String, String> applied = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (base.props.containsKey(e.getKey())) applied.put(e.getKey(), e.getValue());
        }
        int id = volume.intern(Blockstates.makeState(name, applied));
        into.put(name, id);
        return id;
    }

    /**
     * The element's {@code form} param. Elements also use {@code form} for their own variety
     * ({@code "pilaster"}, {@code "pier"}); a value that names no block form reads as a block,
     * which is what the TypeScript's unchecked cast amounts to.
     */
    private static BlockForm formOf(Element e) {
        if (!(e.param("form") instanceof String s)) return BlockForm.BLOCK;
        for (BlockForm f : BlockForm.values()) if (f.id().equals(s)) return f;
        return BlockForm.BLOCK;
    }

    /** Insertion order matches the TS: facing, half/type, hanging, axis. */
    private static Map<String, String> orientationOf(Element e, BlockForm form) {
        Map<String, String> props = new LinkedHashMap<>();
        if (e.params() == null) return props;
        if (e.param("facing") instanceof String f) props.put("facing", f);
        if (Boolean.TRUE.equals(e.param("top"))) props.put(form == BlockForm.SLAB ? "type" : "half", "top");
        if (Boolean.TRUE.equals(e.param("hanging"))) props.put("hanging", "true");
        if (e.param("axis") instanceof String a) props.put("axis", a);
        return props;
    }

    private static double clampByte(double v) {
        if (!Double.isFinite(v)) return 0;
        return Math.max(0, Math.min(255, Math.floor(v + 0.5)));
    }
}
