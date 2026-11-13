package io.github.tjhgibbs.capella.core.detail;

import io.github.tjhgibbs.capella.core.blockstate.BlockState;
import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.params.Params.Lighting;
import io.github.tjhgibbs.capella.core.params.Params.Windows;
import io.github.tjhgibbs.capella.core.raster.RasterPass;
import io.github.tjhgibbs.capella.core.raster.Volume;
import io.github.tjhgibbs.capella.core.small.Style;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The last word on a finished volume's light sources ({@link Params#LIGHTING}) and, on towers and
 * garrison buildings, its window glass ({@link Params#WINDOWS}). Works on the placed blocks, so
 * every generator gets the same treatment without threading the settings through it. Run it
 * before the volume is normalised: iron bars put where full glass stood need their connections.
 */
public final class Finish {
    private Finish() {
    }

    /** Marks a palette entry whose replacement depends on where it stands (stained glass). */
    private static final int PER_VOXEL = -1;

    /** @return whether any block changed */
    public static boolean apply(Volume v, BuildingParams p) {
        Lighting lighting = p.get(Params.LIGHTING);
        Windows windows = Params.glazed(p.get(Params.TYPE)) ? p.get(Params.WINDOWS) : null;
        int n = v.paletteSize();
        int[] map = new int[n];
        boolean any = false;
        for (int id = 0; id < n; id++) {
            map[id] = id;
            if (id == 0) continue;
            BlockState s = v.stateOfId(id);
            BlockState next = light(s, lighting);
            if (windows != null && glass(s.name)) {
                if (windows == Windows.STAINED) {
                    map[id] = PER_VOXEL;
                    any = true;
                    continue;
                }
                next = window(s, windows);
            }
            if (next == s) continue;
            map[id] = next == null ? 0 : v.intern(next);
            any |= map[id] != id;
        }
        if (!any) return false;
        Style.Mix stained = windows == Windows.STAINED ? Style.of(p.get(Params.STONE)).glassRich : null;
        boolean changed = false;
        for (int y = v.minY; y < v.minY + v.sizeY; y++)
            for (int z = v.minZ; z < v.minZ + v.sizeZ; z++)
                for (int x = v.minX; x < v.minX + v.sizeX; x++) {
                    int id = v.idAt(x, y, z);
                    if (id >= n || map[id] == id) continue;
                    int to = map[id];
                    if (to == PER_VOXEL) {
                        BlockState s = v.stateOfId(id);
                        String pane = stained.block(p.seed(), x, y, z, 0);
                        // Full glass blocks take the same colour's block.
                        String name = s.name.endsWith("_pane") ? pane : pane.substring(0, pane.length() - "_pane".length());
                        to = v.intern(new BlockState(name, s.props));
                        if (to == id) continue;
                    }
                    v.setId(x, y, z, to, to == 0 ? RasterPass.NONE : v.passAt(x, y, z));
                    changed = true;
                }
        return changed;
    }

    private static boolean glass(String name) {
        String n = name.startsWith("minecraft:") ? name.substring(10) : name;
        return n.equals("glass") || n.equals("glass_pane") || n.endsWith("_stained_glass") || n.endsWith("_stained_glass_pane");
    }

    /** Null means air. */
    private static BlockState window(BlockState s, Windows w) {
        boolean pane = s.name.endsWith("_pane");
        return switch (w) {
            case GLASS -> new BlockState(pane ? "minecraft:glass_pane" : "minecraft:glass", s.props);
            case IRON_BARS -> new BlockState("minecraft:iron_bars", pane ? s.props : Map.of());
            case EMPTY -> null;
            case STAINED -> throw new IllegalStateException("stained glass is chosen per voxel");
        };
    }

    /** Null means air; the same instance means unchanged. */
    private static BlockState light(BlockState s, Lighting lighting) {
        if (lighting == Lighting.BRIGHT) return s;
        String n = s.name;
        boolean dim = lighting == Lighting.DIM;
        switch (n) {
            case "minecraft:lantern", "minecraft:soul_lantern":
                return dim ? new BlockState("minecraft:soul_lantern", s.props) : null;
            case "minecraft:torch", "minecraft:soul_torch":
                return dim ? new BlockState("minecraft:soul_torch", s.props) : null;
            case "minecraft:wall_torch", "minecraft:soul_wall_torch":
                return dim ? new BlockState("minecraft:soul_wall_torch", s.props) : null;
            case "minecraft:campfire", "minecraft:soul_campfire":
                return dim ? new BlockState("minecraft:soul_campfire", s.props) : with(s, "lit", "false");
            default:
                break;
        }
        if (n.endsWith("candle") && s.prop("candles") != null) {
            if (!dim) return with(s, "lit", "false");
            int count = Integer.parseInt(s.prop("candles"));
            // Half as many, rounded up: a single candle stays.
            return with(s, "candles", Integer.toString((count + 1) / 2));
        }
        return s;
    }

    private static BlockState with(BlockState s, String key, String value) {
        if (value.equals(s.prop(key))) return s;
        Map<String, String> props = new LinkedHashMap<>(s.props);
        props.put(key, value);
        return new BlockState(s.name, props);
    }
}
