package io.github.tjhgibbs.capella.core.preview;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Approximate average texture colours for preview rendering, matched on the block id. Colours
 * are opaque RGB. Unknown blocks fall back to a keyword guess, then to magenta-ish grey.
 */
public final class BlockColors {
    private static final Map<String, Integer> EXACT = new LinkedHashMap<>();
    private static final Map<String, Integer> CACHE = new ConcurrentHashMap<>();

    static {
        // Bark (log side) colours.
        put("oak_log", 0x6D5532); put("oak_wood", 0x6D5532);
        put("spruce_log", 0x3B2812); put("spruce_wood", 0x3B2812);
        put("birch_log", 0xD8D7D2); put("birch_wood", 0xD8D7D2);
        put("jungle_log", 0x56441A); put("jungle_wood", 0x56441A);
        put("acacia_log", 0x686159); put("acacia_wood", 0x686159);
        put("dark_oak_log", 0x3C2E1A); put("dark_oak_wood", 0x3C2E1A);
        put("mangrove_log", 0x544333); put("mangrove_wood", 0x544333);
        put("cherry_log", 0x371C25); put("cherry_wood", 0x371C25);
        put("pale_oak_log", 0x5A504C); put("pale_oak_wood", 0x5A504C);
        put("crimson_stem", 0x6B3344); put("crimson_hyphae", 0x5C1A1E);
        put("warped_stem", 0x3A3B4E); put("warped_hyphae", 0x3A3B4E);
        put("mangrove_roots", 0x4B3D2A);
        // Fences use the plank colour.
        put("oak_fence", 0xA2834F); put("spruce_fence", 0x735531); put("birch_fence", 0xC0AF79);
        put("jungle_fence", 0xA0734D); put("acacia_fence", 0xA85A32); put("dark_oak_fence", 0x432B14);
        put("mangrove_fence", 0x763631); put("cherry_fence", 0xE2B3AC); put("pale_oak_fence", 0xE3D9D6);
        put("crimson_fence", 0x653147); put("warped_fence", 0x2B6863);
        // Leaves, tinted roughly as in a temperate biome.
        put("oak_leaves", 0x4A7A2C); put("spruce_leaves", 0x3A5C3A); put("birch_leaves", 0x6A8C3F);
        put("jungle_leaves", 0x3F8A1E); put("acacia_leaves", 0x6A8A26); put("dark_oak_leaves", 0x3B6B1E);
        put("mangrove_leaves", 0x5E8A2A); put("cherry_leaves", 0xE8A6C6); put("pale_oak_leaves", 0x7A8476);
        put("azalea_leaves", 0x5A7A2A); put("flowering_azalea_leaves", 0x7D7A4E);
        put("nether_wart_block", 0x7A0A0A); put("warped_wart_block", 0x167E86); put("shroomlight", 0xF09A48);
        put("weeping_vines", 0x8A1B12); put("weeping_vines_plant", 0x8A1B12);
        put("pale_hanging_moss", 0x6E7468); put("vine", 0x3E6E1E);
        put("moss_block", 0x597A2B); put("mud_brick_wall", 0x8A6A4F); put("cobblestone_wall", 0x7A7A7A);
        put("mossy_cobblestone_wall", 0x6E7A5A);
        put("grass_block", 0x6A9A3A); put("dirt", 0x86603F);
    }

    private static void put(String name, int rgb) {
        EXACT.put(name, rgb);
    }

    private BlockColors() {
    }

    /** RGB for a block state string such as {@code minecraft:oak_log[axis=y]}. */
    public static int rgb(String state) {
        return CACHE.computeIfAbsent(state, BlockColors::lookup);
    }

    private static int lookup(String state) {
        String id = state;
        int b = id.indexOf('[');
        if (b >= 0) {
            id = id.substring(0, b);
        }
        int colon = id.indexOf(':');
        String name = colon >= 0 ? id.substring(colon + 1) : id;
        Integer c = EXACT.get(name);
        if (c != null) {
            return c;
        }
        if (name.contains("leaves")) {
            return 0x4A7A2C;
        }
        if (name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_stem")) {
            return 0x5E4A30;
        }
        if (name.contains("fence") || name.contains("planks")) {
            return 0x9A7A4A;
        }
        if (name.contains("wall") || name.contains("stone")) {
            return 0x7A7A7A;
        }
        return 0x9A8A9A;
    }

    public static boolean isFoliage(String state) {
        return state.contains("leaves") || state.contains("wart_block") || state.contains("vine")
                || state.contains("moss") || state.contains("shroomlight");
    }
}
