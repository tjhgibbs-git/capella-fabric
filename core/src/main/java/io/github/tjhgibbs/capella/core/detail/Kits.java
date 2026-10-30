package io.github.tjhgibbs.capella.core.detail;

import io.github.tjhgibbs.capella.core.palette.Material;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;
import io.github.tjhgibbs.capella.core.palette.Palette;
import io.github.tjhgibbs.capella.core.palette.RoleEntry;
import io.github.tjhgibbs.capella.core.params.Params.Stone;
import io.github.tjhgibbs.capella.core.small.Style;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The detail stone for each building stone. Courses, hoods and capitals are one dressed stone a
 * step lighter than the walling; the plinth a step darker; recess backs darker still, so a
 * carved panel reads as shadow even in flat light.
 */
public final class Kits {
    private Kits() {
    }

    private record Dress(String course, String courseStairs, String plinth, String plinthStairs, String shaft, String quoin,
                         List<String> shadow) {
    }

    private static String mc(String id) {
        return "minecraft:" + id;
    }

    private static Dress dress(Stone stone) {
        return switch (stone) {
            case LIMESTONE -> new Dress("stone", "stone_stairs", "tuff_bricks", "tuff_brick_stairs", "andesite_wall", "smooth_stone",
                    List.of("andesite", "cracked_stone_bricks", "tuff"));
            case DEEPSLATE -> new Dress("polished_deepslate", "polished_deepslate_stairs", "cobbled_deepslate", "cobbled_deepslate_stairs",
                    "deepslate_tile_wall", "deepslate_tiles", List.of("cobbled_deepslate", "cracked_deepslate_tiles"));
            case SANDSTONE -> new Dress("smooth_sandstone", "smooth_sandstone_stairs", "red_sandstone", "red_sandstone_stairs",
                    "sandstone_wall", "cut_sandstone", List.of("mud_bricks", "packed_mud"));
            case BLACKSTONE -> new Dress("polished_blackstone", "polished_blackstone_stairs", "blackstone", "blackstone_stairs",
                    "polished_blackstone_brick_wall", "polished_deepslate", List.of("blackstone", "cobbled_deepslate"));
        };
    }

    private static Detailer.Kit kit(Stone stone, Set<String> body, Set<String> trim) {
        Dress d = dress(stone);
        trim.removeAll(body);
        return new Detailer.Kit(body, trim, mc(d.course), mc(d.courseStairs), mc(d.plinth), mc(d.plinthStairs), mc(d.shaft),
                mc(d.quoin), d.shadow.stream().map(Kits::mc).toList());
    }

    /** For a cathedral rastered from {@code palette}: its main masonry is the walling. */
    public static Detailer.Kit cathedral(Stone stone, Palette palette) {
        Set<String> body = new HashSet<>();
        RoleEntry main = palette.roles.get(MaterialRole.MASONRY_MAIN);
        for (Material m : main.materials) body.add(m.block());
        if (main.weathered != null) for (Material m : main.weathered) body.add(m.block());
        Set<String> trim = new HashSet<>();
        for (MaterialRole r : List.of(MaterialRole.MASONRY_TRIM, MaterialRole.MASONRY_FINE, MaterialRole.RIB, MaterialRole.TRACERY)) {
            RoleEntry e = palette.roles.get(r);
            for (Material m : e.materials) trim.addAll(m.forms.values());
            if (e.weathered != null) for (Material m : e.weathered) trim.addAll(m.forms.values());
        }
        return kit(stone, body, trim);
    }

    /** For the block-by-block builders: the wall mix is the walling, their trim and plinth are dressed. */
    public static Detailer.Kit small(Stone stone) {
        Style st = Style.of(stone);
        Set<String> body = new HashSet<>(st.wall.blocks());
        Set<String> trim = new HashSet<>(st.trim.blocks());
        trim.addAll(st.plinth.blocks());
        return kit(stone, body, trim);
    }
}
