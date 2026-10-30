package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.hash.Hash;
import io.github.tjhgibbs.capella.core.params.Params.Stone;

import java.util.ArrayList;
import java.util.List;

/**
 * The block-level materials of a small church, by stone. Mixes follow the usual builders' rules:
 * neighbouring values only, laid in clusters (noise, not per-block confetti), roughly 60/30/10,
 * with a gradient that darkens toward the ground.
 */
public final class Style {
    /** One material and its shaped forms; a null form falls back to the mix's first that has it. */
    public record Suite(String block, String stairs, String slab, String wall) {
    }

    /** A clustered blend of suites. List order is light to dark, the main one in the middle. */
    public static final class Mix {
        private final String key;
        private final List<Suite> suites;
        private final double[] cumulative;

        Mix(String key, Object... suiteWeights) {
            this.key = key;
            this.suites = new ArrayList<>();
            List<Double> w = new ArrayList<>();
            for (int i = 0; i < suiteWeights.length; i += 2) {
                suites.add((Suite) suiteWeights[i]);
                w.add(((Number) suiteWeights[i + 1]).doubleValue());
            }
            double total = w.stream().mapToDouble(Double::doubleValue).sum();
            cumulative = new double[w.size()];
            double run = 0;
            for (int i = 0; i < w.size(); i++) {
                run += w.get(i) / total;
                cumulative[i] = run;
            }
        }

        /** {@code bias} pushes the draw toward the dark end of the list (0 = none, 0.3 = strongly). */
        public Suite pick(double seed, int x, int y, int z, double bias) {
            if (suites.size() == 1) return suites.get(0);
            double cluster = Hash.noise3(seed, key, x, y * 1.6, z, 3.2);
            double grain = Hash.hash(seed, key, x, y, z);
            // Value noise bunches round 0.5: stretch it back out so the end members turn up.
            double v = 0.5 + (cluster - 0.5) * 1.9;
            v = 0.72 * v + 0.28 * grain + bias;
            v = Math.max(0, Math.min(0.99999, v));
            for (int i = 0; i < cumulative.length; i++) if (v < cumulative[i]) return suites.get(i);
            return suites.get(suites.size() - 1);
        }

        public String block(double seed, int x, int y, int z, double bias) {
            return pick(seed, x, y, z, bias).block();
        }

        public String stairs(double seed, int x, int y, int z, double bias) {
            Suite s = pick(seed, x, y, z, bias);
            if (s.stairs() != null) return s.stairs();
            for (Suite o : suites) if (o.stairs() != null) return o.stairs();
            throw new IllegalStateException(key + ": no stairs");
        }

        public String slab(double seed, int x, int y, int z, double bias) {
            Suite s = pick(seed, x, y, z, bias);
            if (s.slab() != null) return s.slab();
            for (Suite o : suites) if (o.slab() != null) return o.slab();
            throw new IllegalStateException(key + ": no slab");
        }

        public String wall(double seed, int x, int y, int z, double bias) {
            Suite s = pick(seed, x, y, z, bias);
            if (s.wall() != null) return s.wall();
            for (Suite o : suites) if (o.wall() != null) return o.wall();
            throw new IllegalStateException(key + ": no wall");
        }
    }

    public final Mix wall, trim, plinth, roof, ridge, floorField, floorAccent, floorBorder, sanctuary;
    /** Plain glass for the nave lights, rich glass for the east window and the rose. */
    public final Mix glassPlain, glassRich;
    public final String timber, timberLog, timberStairs, timberSlab, timberFence, timberTrapdoor, door, lining, button;
    public final String carpet;

    private Style(Mix wall, Mix trim, Mix plinth, Mix roof, Mix ridge, Mix floorField, Mix floorAccent, Mix floorBorder,
                  Mix sanctuary, Mix glassPlain, Mix glassRich, String wood, String lining, String button, String carpet) {
        this.wall = wall;
        this.trim = trim;
        this.plinth = plinth;
        this.roof = roof;
        this.ridge = ridge;
        this.floorField = floorField;
        this.floorAccent = floorAccent;
        this.floorBorder = floorBorder;
        this.sanctuary = sanctuary;
        this.glassPlain = glassPlain;
        this.glassRich = glassRich;
        this.timber = "minecraft:" + wood + "_planks";
        this.timberLog = "minecraft:stripped_" + wood + "_log";
        this.timberStairs = "minecraft:" + wood + "_stairs";
        this.timberSlab = "minecraft:" + wood + "_slab";
        this.timberFence = "minecraft:" + wood + "_fence";
        this.timberTrapdoor = "minecraft:" + wood + "_trapdoor";
        this.door = "minecraft:" + wood + "_door";
        this.lining = "minecraft:" + lining + "_planks";
        this.button = button;
        this.carpet = carpet;
    }

    private static Suite s(String block, String stairs, String slab, String wall) {
        return new Suite(mc(block), mc(stairs), mc(slab), mc(wall));
    }

    private static Suite b(String block) {
        return new Suite(mc(block), null, null, null);
    }

    private static String mc(String id) {
        return id == null ? null : "minecraft:" + id;
    }

    private static Mix glass(String key, Object... colourWeights) {
        Object[] out = new Object[colourWeights.length];
        for (int i = 0; i < colourWeights.length; i += 2) {
            String c = (String) colourWeights[i];
            String pane = c.equals("clear") ? "glass_pane" : c + "_stained_glass_pane";
            out[i] = new Suite(mc(pane), null, null, null);
            out[i + 1] = colourWeights[i + 1];
        }
        return new Mix(key, out);
    }

    public static Style of(Stone stone) {
        return switch (stone) {
            case LIMESTONE -> new Style(
                    new Mix("wall", s("stone", "stone_stairs", "stone_slab", null), 10,
                            s("stone_bricks", "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall"), 55,
                            b("cracked_stone_bricks"), 18,
                            s("andesite", "andesite_stairs", "andesite_slab", "andesite_wall"), 10,
                            s("mossy_stone_bricks", "mossy_stone_brick_stairs", "mossy_stone_brick_slab", "mossy_stone_brick_wall"), 7),
                    new Mix("trim", s("polished_andesite", "polished_andesite_stairs", "polished_andesite_slab", "andesite_wall"), 1),
                    new Mix("plinth", s("tuff_bricks", "tuff_brick_stairs", "tuff_brick_slab", "tuff_brick_wall"), 6,
                            s("polished_tuff", "polished_tuff_stairs", "polished_tuff_slab", "polished_tuff_wall"), 3,
                            s("cobblestone", "cobblestone_stairs", "cobblestone_slab", "cobblestone_wall"), 2),
                    new Mix("roof", s("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall"), 65,
                            s("cobbled_deepslate", "cobbled_deepslate_stairs", "cobbled_deepslate_slab", "cobbled_deepslate_wall"), 25,
                            s("deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall"), 10),
                    new Mix("ridge", s("deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall"), 1),
                    new Mix("floor", b("polished_andesite"), 1),
                    new Mix("floor-accent", b("stone_bricks"), 1),
                    new Mix("floor-border", b("polished_tuff"), 1),
                    new Mix("sanctuary", b("calcite"), 1),
                    glass("glass", "light_gray", 3, "white", 5, "clear", 3, "light_blue", 1),
                    glass("glass-rich", "blue", 4, "light_blue", 2, "red", 3, "yellow", 1, "purple", 1),
                    "dark_oak", "spruce", "minecraft:stone_button", "minecraft:red_carpet");
            case DEEPSLATE -> new Style(
                    new Mix("wall", s("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab", "polished_deepslate_wall"), 10,
                            s("deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall"), 55,
                            b("cracked_deepslate_bricks"), 15,
                            s("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall"), 12,
                            s("cobbled_deepslate", "cobbled_deepslate_stairs", "cobbled_deepslate_slab", "cobbled_deepslate_wall"), 8),
                    new Mix("trim", s("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab", "polished_deepslate_wall"), 1),
                    new Mix("plinth", s("cobbled_deepslate", "cobbled_deepslate_stairs", "cobbled_deepslate_slab", "cobbled_deepslate_wall"), 1),
                    new Mix("roof", s("waxed_weathered_cut_copper", "waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper_slab", null), 3,
                            s("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab", null), 7),
                    new Mix("ridge", s("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab", "polished_deepslate_wall"), 1),
                    new Mix("floor", b("polished_deepslate"), 1),
                    new Mix("floor-accent", b("deepslate_tiles"), 1),
                    new Mix("floor-border", b("cobbled_deepslate"), 1),
                    new Mix("sanctuary", b("polished_basalt"), 1),
                    glass("glass", "light_gray", 4, "cyan", 2, "light_blue", 2, "gray", 1),
                    glass("glass-rich", "blue", 4, "purple", 2, "cyan", 2, "magenta", 1, "red", 1),
                    "spruce", "spruce", "minecraft:polished_blackstone_button", "minecraft:purple_carpet");
            case SANDSTONE -> new Style(
                    new Mix("wall", s("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab", null), 15,
                            s("sandstone", "sandstone_stairs", "sandstone_slab", "sandstone_wall"), 55,
                            b("cut_sandstone"), 22,
                            b("chiseled_sandstone"), 3,
                            s("mud_bricks", "mud_brick_stairs", "mud_brick_slab", "mud_brick_wall"), 5),
                    new Mix("trim", s("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab", "sandstone_wall"), 1),
                    new Mix("plinth", s("mud_bricks", "mud_brick_stairs", "mud_brick_slab", "mud_brick_wall"), 1),
                    new Mix("roof", s("bricks", "brick_stairs", "brick_slab", "brick_wall"), 7,
                            s("granite", "granite_stairs", "granite_slab", "granite_wall"), 3),
                    new Mix("ridge", s("cut_red_sandstone", "red_sandstone_stairs", "cut_red_sandstone_slab", "red_sandstone_wall"), 1),
                    new Mix("floor", b("smooth_sandstone"), 1),
                    new Mix("floor-accent", b("cut_red_sandstone"), 1),
                    new Mix("floor-border", b("terracotta"), 1),
                    new Mix("sanctuary", b("chiseled_sandstone"), 1),
                    glass("glass", "white", 5, "yellow", 2, "clear", 2, "orange", 1),
                    glass("glass-rich", "orange", 3, "yellow", 2, "red", 2, "light_blue", 2, "blue", 1),
                    "jungle", "birch", "minecraft:birch_button", "minecraft:orange_carpet");
            case BLACKSTONE -> new Style(
                    new Mix("wall", s("polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab", "polished_blackstone_wall"), 12,
                            s("polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab", "polished_blackstone_brick_wall"), 58,
                            b("cracked_polished_blackstone_bricks"), 18,
                            s("blackstone", "blackstone_stairs", "blackstone_slab", "blackstone_wall"), 12),
                    new Mix("trim", s("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab", "polished_deepslate_wall"), 1),
                    new Mix("plinth", s("blackstone", "blackstone_stairs", "blackstone_slab", "blackstone_wall"), 1),
                    new Mix("roof", s("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall"), 6,
                            s("deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall"), 4),
                    new Mix("ridge", s("polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab", "polished_blackstone_wall"), 1),
                    new Mix("floor", b("polished_blackstone"), 1),
                    new Mix("floor-accent", b("gilded_blackstone"), 1),
                    new Mix("floor-border", b("blackstone"), 1),
                    new Mix("sanctuary", b("crimson_planks"), 1),
                    glass("glass", "gray", 3, "red", 2, "black", 1, "orange", 1),
                    glass("glass-rich", "red", 4, "orange", 2, "yellow", 1, "purple", 2),
                    "dark_oak", "crimson", "minecraft:polished_blackstone_button", "minecraft:red_carpet");
        };
    }
}
