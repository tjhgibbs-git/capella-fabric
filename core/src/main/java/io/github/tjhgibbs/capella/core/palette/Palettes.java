package io.github.tjhgibbs.capella.core.palette;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shipped palettes (Capella {@code palette.ts}, generated from its tables). Every one is
 * complete over {@link MaterialRole} once the fallback chain has run; roles the cathedral never
 * asks for (plants, clutter, debris) are kept so the tables stay one-to-one with the TypeScript.
 */
public final class Palettes {
    private Palettes() {
    }

    public static final String DEFAULT_PALETTE_ID = "limestone";

    private static Material mat(String block, Map<BlockForm, String> extra, double weight) {
        Map<BlockForm, String> forms = new EnumMap<>(BlockForm.class);
        forms.putAll(extra);
        forms.put(BlockForm.BLOCK, block);
        return new Material(forms, weight);
    }

    private static Material suite(String block, String prefix, double weight) {
        return suite(block, prefix, weight, true);
    }

    private static Material suite(String block, String prefix, double weight, boolean wall) {
        Map<BlockForm, String> forms = new EnumMap<>(BlockForm.class);
        forms.put(BlockForm.BLOCK, block);
        forms.put(BlockForm.SLAB, prefix + "_slab");
        forms.put(BlockForm.STAIRS, prefix + "_stairs");
        if (wall) forms.put(BlockForm.WALL, prefix + "_wall");
        return new Material(forms, weight);
    }

    private static Material wood(String kind, double weight) {
        Map<BlockForm, String> forms = new EnumMap<>(BlockForm.class);
        forms.put(BlockForm.BLOCK, "minecraft:" + kind + "_planks");
        forms.put(BlockForm.SLAB, "minecraft:" + kind + "_slab");
        forms.put(BlockForm.STAIRS, "minecraft:" + kind + "_stairs");
        forms.put(BlockForm.FENCE, "minecraft:" + kind + "_fence");
        forms.put(BlockForm.TRAPDOOR, "minecraft:" + kind + "_trapdoor");
        forms.put(BlockForm.LADDER, "minecraft:ladder");
        return new Material(forms, weight);
    }

    private static RoleEntry glass(String colour) {
        return new RoleEntry(
                List.of(mat("minecraft:" + colour + "_stained_glass", Map.of(BlockForm.PANE, "minecraft:" + colour + "_stained_glass_pane"), 1)),
                List.of(mat("minecraft:glass", Map.of(BlockForm.PANE, "minecraft:glass_pane"), 1)));
    }

    /** Stained glass as a mosaic: colour, weight, colour, weight... The palette's noise sets the pieces. */
    private static RoleEntry mosaic(Object... colourWeights) {
        List<Material> ms = new ArrayList<>();
        for (int i = 0; i < colourWeights.length; i += 2) {
            String c = (String) colourWeights[i];
            ms.add(mat("minecraft:" + c + "_stained_glass", Map.of(BlockForm.PANE, "minecraft:" + c + "_stained_glass_pane"),
                    ((Number) colourWeights[i + 1]).doubleValue()));
        }
        return new RoleEntry(ms);
    }

    static final Material stoneBrick = suite("minecraft:stone_bricks", "minecraft:stone_brick", 6);
    static final Material crackedStoneBrick = mat("minecraft:cracked_stone_bricks", Map.of(), 2);
    static final Material chiseledStoneBrick = mat("minecraft:chiseled_stone_bricks", Map.of(), 1);
    static final Material mossyStoneBrick = suite("minecraft:mossy_stone_bricks", "minecraft:mossy_stone_brick", 5);
    static final Material cobble = suite("minecraft:cobblestone", "minecraft:cobblestone", 3);
    static final Material mossyCobble = suite("minecraft:mossy_cobblestone", "minecraft:mossy_cobblestone", 2);
    static final Material polishedDiorite = suite("minecraft:polished_diorite", "minecraft:polished_diorite", 4, false);
    static final Material diorite = suite("minecraft:diorite", "minecraft:diorite", 2);
    static final Material calcite = mat("minecraft:calcite", Map.of(), 3);
    static final Material smoothQuartz = suite("minecraft:smooth_quartz", "minecraft:smooth_quartz", 3, false);
    static final Material polishedAndesite = suite("minecraft:polished_andesite", "minecraft:polished_andesite", 3, false);
    static final Material andesite = suite("minecraft:andesite", "minecraft:andesite", 2);
    static final Material smoothStone = mat("minecraft:smooth_stone", Map.of(BlockForm.SLAB, "minecraft:smooth_stone_slab"), 3);
    static final Material deepslateBrick = suite("minecraft:deepslate_bricks", "minecraft:deepslate_brick", 6);
    static final Material crackedDeepslateBrick = mat("minecraft:cracked_deepslate_bricks", Map.of(), 2);
    static final Material deepslateTile = suite("minecraft:deepslate_tiles", "minecraft:deepslate_tile", 5);
    static final Material crackedDeepslateTile = mat("minecraft:cracked_deepslate_tiles", Map.of(), 2);
    static final Material cobbledDeepslate = suite("minecraft:cobbled_deepslate", "minecraft:cobbled_deepslate", 3);
    static final Material polishedDeepslate = suite("minecraft:polished_deepslate", "minecraft:polished_deepslate", 3);
    static final Material sandstone = suite("minecraft:sandstone", "minecraft:sandstone", 5);
    static final Material cutSandstone = mat("minecraft:cut_sandstone", Map.of(BlockForm.SLAB, "minecraft:cut_sandstone_slab"), 2);
    static final Material chiseledSandstone = mat("minecraft:chiseled_sandstone", Map.of(), 1);
    static final Material smoothSandstone = suite("minecraft:smooth_sandstone", "minecraft:smooth_sandstone", 3, false);
    static final Material redSandstone = suite("minecraft:red_sandstone", "minecraft:red_sandstone", 3);
    static final Material blackstone = suite("minecraft:blackstone", "minecraft:blackstone", 4);
    static final Material polishedBlackstoneBrick = suite( "minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_brick", 6);
    static final Material crackedPolishedBlackstoneBrick = mat("minecraft:cracked_polished_blackstone_bricks", Map.of(), 2);
    static final Material polishedBlackstone = suite("minecraft:polished_blackstone", "minecraft:polished_blackstone", 3);
    static final Material basalt = mat("minecraft:polished_basalt", Map.of(), 3);
    static final Material gravel = mat("minecraft:gravel", Map.of(), 2);
    static final Material tuff = suite("minecraft:tuff", "minecraft:tuff", 3);
    static final Material tuffBrick = suite("minecraft:tuff_bricks", "minecraft:tuff_brick", 3);
    static final Material bricks = suite("minecraft:bricks", "minecraft:brick", 6);
    static final Material mudBricks = suite("minecraft:mud_bricks", "minecraft:mud_brick", 3);
    static final Material packedMud = mat("minecraft:packed_mud", Map.of(), 2);
    static final Material whiteTerracotta = mat("minecraft:white_terracotta", Map.of(), 6);
    static final Material lightGrayTerracotta = mat("minecraft:light_gray_terracotta", Map.of(), 2);
    static final Material clay = mat("minecraft:clay", Map.of(), 2);
    static final Material granite = suite("minecraft:granite", "minecraft:granite", 3);
    static final Material darkOak = wood("dark_oak", 4);
    static final Material spruce = wood("spruce", 3);
    static final Material oak = wood("oak", 2);
    static final Material birch = wood("birch", 2);
    static final Material darkOakLog = mat("minecraft:dark_oak_log", Map.of(), 3);
    static final Material strippedDarkOakLog = mat("minecraft:stripped_dark_oak_log", Map.of(), 2);
    static final Material spruceLog = mat("minecraft:spruce_log", Map.of(), 3);
    static final Material strippedSpruceLog = mat("minecraft:stripped_spruce_log", Map.of(), 1);
    static final Material oakLog = mat("minecraft:oak_log", Map.of(), 4);
    static final Material birchLog = mat("minecraft:birch_log", Map.of(), 2);
    static final Material strippedOakLog = mat("minecraft:stripped_oak_log", Map.of(), 2);
    static final Material grass = mat("minecraft:grass_block", Map.of(), 6);
    static final Material stone = suite("minecraft:stone", "minecraft:stone", 5, false);
    static final Material deepslate = mat("minecraft:deepslate", Map.of(), 4);
    static final Material dirt = mat("minecraft:dirt", Map.of(), 4);
    static final Material coarseDirt = mat("minecraft:coarse_dirt", Map.of(), 3);
    static final Material rootedDirt = mat("minecraft:rooted_dirt", Map.of(), 2);
    static final Material podzol = mat("minecraft:podzol", Map.of(), 2);
    static final Material sand = mat("minecraft:sand", Map.of(), 4);
    static final Material vine = mat("minecraft:vine", Map.of(), 5);
    static final Material glowLichen = mat("minecraft:glow_lichen", Map.of(), 1);
    static final Material moss = mat("minecraft:moss_block", Map.of(BlockForm.CARPET, "minecraft:moss_carpet"), 4);
    static final Material shortGrass = mat("minecraft:short_grass", Map.of(), 5);
    static final Material fern = mat("minecraft:fern", Map.of(), 2);
    static final Material deadBush = mat("minecraft:dead_bush", Map.of(), 1);
    static final Material oakLeaves = mat("minecraft:oak_leaves", Map.of(), 3);
    static final Material birchLeaves = mat("minecraft:birch_leaves", Map.of(), 2);
    static final Material azalea = mat("minecraft:flowering_azalea_leaves", Map.of(), 1);
    static final Material lilyPad = mat("minecraft:lily_pad", Map.of(), 1);
    static final Material sugarCane = mat("minecraft:sugar_cane", Map.of(), 1);
    static final Material seagrass = mat("minecraft:seagrass", Map.of(), 1);
    static final Material craftingTable = mat("minecraft:crafting_table", Map.of(), 3);
    static final Material cartographyTable = mat("minecraft:cartography_table", Map.of(), 2);
    static final Material smithingTable = mat("minecraft:smithing_table", Map.of(), 2);
    static final Material fletchingTable = mat("minecraft:fletching_table", Map.of(), 2);
    static final Material bookshelf = mat("minecraft:bookshelf", Map.of(), 2);
    static final Material composter = mat("minecraft:composter", Map.of(), 2);
    static final Material hayBlock = mat("minecraft:hay_block", Map.of(), 2);
    static final Material scaffolding = mat("minecraft:scaffolding", Map.of(), 1);
    static final Material chain = mat("minecraft:chain", Map.of(), 1);
    static final Material ironBars = mat("minecraft:iron_bars", Map.of(), 1);
    static final Material soulLantern = mat("minecraft:soul_lantern", Map.of(), 1);
    static final Material lantern = mat("minecraft:lantern", Map.of(), 1);

    private static void hardware(Map<MaterialRole, RoleEntry> r) {
        r.put(MaterialRole.STAGING, new RoleEntry(List.of(scaffolding)));
        r.put(MaterialRole.CHAIN, new RoleEntry(List.of(chain)));
        r.put(MaterialRole.PORTCULLIS, new RoleEntry(List.of(ironBars)));
    }

    private static Palette limestone() {
        Map<MaterialRole, RoleEntry> r = new EnumMap<>(MaterialRole.class);
        r.put(MaterialRole.MASONRY_MAIN, new RoleEntry(List.of(stoneBrick, crackedStoneBrick, chiseledStoneBrick), List.of(mossyStoneBrick, crackedStoneBrick, mossyCobble)));
        r.put(MaterialRole.MASONRY_TRIM, new RoleEntry(List.of(polishedDiorite, diorite), List.of(diorite, mossyCobble)));
        r.put(MaterialRole.MASONRY_FINE, new RoleEntry(List.of(calcite, smoothQuartz), List.of(calcite, polishedAndesite)));
        r.put(MaterialRole.RIB, new RoleEntry(List.of(polishedDiorite), List.of(diorite)));
        r.put(MaterialRole.TRACERY, new RoleEntry(List.of(smoothQuartz, diorite), List.of(diorite, mossyCobble)));
        r.put(MaterialRole.FLOOR, new RoleEntry(List.of(polishedAndesite, smoothStone), List.of(andesite, cobble)));
        r.put(MaterialRole.FLOOR_WORN, new RoleEntry(List.of(cobble, crackedStoneBrick, gravel), List.of(mossyCobble, gravel)));
        r.put(MaterialRole.ROOF, new RoleEntry(List.of(deepslateTile), List.of(crackedDeepslateTile, cobbledDeepslate)));
        r.put(MaterialRole.TIMBER, new RoleEntry(List.of(darkOak, darkOakLog), List.of(darkOak, spruceLog)));
        r.put(MaterialRole.GLASS_A, mosaic("blue", 4, "light_blue", 3, "cyan", 1, "red", 1, "yellow", 1));
        r.put(MaterialRole.GLASS_B, mosaic("blue", 3, "red", 3, "yellow", 1, "purple", 1, "light_blue", 1));
        r.put(MaterialRole.GLASS_C, glass("yellow"));
        r.put(MaterialRole.GLASS_D, glass("green"));
        r.put(MaterialRole.GLASS_E, glass("purple"));
        r.put(MaterialRole.RUBBLE_STONE, new RoleEntry(List.of(cobble, mossyCobble, gravel, andesite)));
        r.put(MaterialRole.RUBBLE_TIMBER, new RoleEntry(List.of(darkOakLog, darkOak)));
        r.put(MaterialRole.RUBBLE_TILE, new RoleEntry(List.of(crackedDeepslateTile, cobbledDeepslate)));
        r.put(MaterialRole.SOIL, new RoleEntry(List.of(dirt, coarseDirt, rootedDirt), List.of(podzol, coarseDirt)));
        r.put(MaterialRole.SUBSOIL, new RoleEntry(List.of(coarseDirt, gravel, rootedDirt), List.of(gravel, coarseDirt)));
        r.put(MaterialRole.TURF, new RoleEntry(List.of(grass), List.of(podzol, moss)));
        r.put(MaterialRole.ROCK, new RoleEntry(List.of(stone, andesite, cobble), List.of(cobble, mossyCobble, gravel)));
        r.put(MaterialRole.SAND, new RoleEntry(List.of(sand, gravel), List.of(sand, coarseDirt)));
        r.put(MaterialRole.PLANT_IVY, new RoleEntry(List.of(vine, glowLichen)));
        r.put(MaterialRole.PLANT_GROUND, new RoleEntry(List.of(shortGrass, fern, deadBush)));
        r.put(MaterialRole.PLANT_MOSS, new RoleEntry(List.of(moss)));
        r.put(MaterialRole.PLANT_CANOPY, new RoleEntry(List.of(oakLeaves, birchLeaves, azalea)));
        r.put(MaterialRole.PLANT_TRUNK, new RoleEntry(List.of(oakLog, birchLog, darkOakLog)));
        r.put(MaterialRole.PLANT_TRUNK_DEAD, new RoleEntry(List.of(strippedOakLog, strippedSpruceLog)));
        r.put(MaterialRole.PLANT_FLOATING, new RoleEntry(List.of(lilyPad)));
        r.put(MaterialRole.PLANT_REED, new RoleEntry(List.of(sugarCane)));
        r.put(MaterialRole.PLANT_ALGAE, new RoleEntry(List.of(seagrass)));
        r.put(MaterialRole.CLUTTER_CRATE, new RoleEntry(List.of(craftingTable, cartographyTable, bookshelf), List.of(composter, craftingTable, hayBlock)));
        r.put(MaterialRole.CLUTTER_FURNITURE, new RoleEntry(List.of(darkOak, spruce), List.of(spruce, oak)));
        hardware(r);
        r.put(MaterialRole.LIGHT, new RoleEntry(List.of(soulLantern)));
        r.put(MaterialRole.DEBRIS_ROOF, new RoleEntry(List.of(deepslateTile, cobbledDeepslate)));
        r.put(MaterialRole.DEBRIS_STONE, new RoleEntry(List.of(cobble, andesite), List.of(mossyCobble, cobble)));
        r.put(MaterialRole.DEBRIS_ORGANIC, new RoleEntry(List.of(darkOak, spruce, oak)));
        return new Palette("limestone", "Limestone", r);
    }

    private static Palette deepslate() {
        Map<MaterialRole, RoleEntry> r = new EnumMap<>(MaterialRole.class);
        r.put(MaterialRole.MASONRY_MAIN, new RoleEntry(List.of(deepslateBrick, crackedDeepslateBrick, polishedDeepslate), List.of(cobbledDeepslate, crackedDeepslateBrick, mossyCobble)));
        r.put(MaterialRole.MASONRY_TRIM, new RoleEntry(List.of(polishedDeepslate, basalt), List.of(cobbledDeepslate)));
        r.put(MaterialRole.MASONRY_FINE, new RoleEntry(List.of(deepslateTile, polishedDeepslate), List.of(crackedDeepslateTile)));
        r.put(MaterialRole.RIB, new RoleEntry(List.of(polishedDeepslate), List.of(cobbledDeepslate)));
        r.put(MaterialRole.TRACERY, new RoleEntry(List.of(polishedDeepslate, deepslateBrick), List.of(cobbledDeepslate)));
        r.put(MaterialRole.FLOOR, new RoleEntry(List.of(deepslateTile, polishedDeepslate), List.of(crackedDeepslateTile, cobbledDeepslate)));
        r.put(MaterialRole.FLOOR_WORN, new RoleEntry(List.of(cobbledDeepslate, crackedDeepslateTile, gravel)));
        r.put(MaterialRole.ROOF, new RoleEntry(List.of(blackstone), List.of(basalt, cobbledDeepslate)));
        r.put(MaterialRole.TIMBER, new RoleEntry(List.of(spruce, spruceLog), List.of(spruce, strippedSpruceLog)));
        r.put(MaterialRole.GLASS_A, mosaic("blue", 4, "purple", 2, "cyan", 1, "magenta", 1));
        r.put(MaterialRole.GLASS_B, mosaic("red", 3, "blue", 3, "orange", 1, "purple", 1));
        r.put(MaterialRole.GLASS_C, glass("orange"));
        r.put(MaterialRole.GLASS_D, glass("lime"));
        r.put(MaterialRole.GLASS_E, glass("magenta"));
        r.put(MaterialRole.RUBBLE_STONE, new RoleEntry(List.of(cobbledDeepslate, gravel, tuff)));
        r.put(MaterialRole.RUBBLE_TIMBER, new RoleEntry(List.of(spruceLog, spruce)));
        r.put(MaterialRole.RUBBLE_TILE, new RoleEntry(List.of(crackedDeepslateTile, blackstone)));
        r.put(MaterialRole.SOIL, new RoleEntry(List.of(coarseDirt, rootedDirt), List.of(podzol)));
        r.put(MaterialRole.SUBSOIL, new RoleEntry(List.of(coarseDirt, gravel, tuff), List.of(gravel, tuff)));
        r.put(MaterialRole.TURF, new RoleEntry(List.of(podzol, moss), List.of(coarseDirt, moss)));
        r.put(MaterialRole.ROCK, new RoleEntry(List.of(deepslate, tuff, cobbledDeepslate), List.of(cobbledDeepslate, gravel)));
        r.put(MaterialRole.SAND, new RoleEntry(List.of(gravel, coarseDirt), List.of(gravel, podzol)));
        r.put(MaterialRole.PLANT_IVY, new RoleEntry(List.of(vine, glowLichen)));
        r.put(MaterialRole.PLANT_GROUND, new RoleEntry(List.of(fern, shortGrass, deadBush)));
        r.put(MaterialRole.PLANT_MOSS, new RoleEntry(List.of(moss)));
        r.put(MaterialRole.PLANT_CANOPY, new RoleEntry(List.of(oakLeaves, azalea)));
        r.put(MaterialRole.PLANT_TRUNK, new RoleEntry(List.of(darkOakLog, spruceLog)));
        r.put(MaterialRole.PLANT_TRUNK_DEAD, new RoleEntry(List.of(strippedSpruceLog)));
        r.put(MaterialRole.PLANT_FLOATING, new RoleEntry(List.of(lilyPad)));
        r.put(MaterialRole.PLANT_REED, new RoleEntry(List.of(sugarCane)));
        r.put(MaterialRole.PLANT_ALGAE, new RoleEntry(List.of(seagrass)));
        r.put(MaterialRole.CLUTTER_CRATE, new RoleEntry(List.of(craftingTable, smithingTable, bookshelf), List.of(composter, smithingTable)));
        r.put(MaterialRole.CLUTTER_FURNITURE, new RoleEntry(List.of(spruce, darkOak), List.of(spruce, oak)));
        hardware(r);
        r.put(MaterialRole.LIGHT, new RoleEntry(List.of(soulLantern)));
        r.put(MaterialRole.DEBRIS_ROOF, new RoleEntry(List.of(blackstone, cobbledDeepslate)));
        r.put(MaterialRole.DEBRIS_STONE, new RoleEntry(List.of(cobbledDeepslate, tuff), List.of(cobbledDeepslate, mossyCobble)));
        r.put(MaterialRole.DEBRIS_ORGANIC, new RoleEntry(List.of(spruce, darkOak)));
        return new Palette("deepslate", "Deepslate", r);
    }

    private static Palette sandstone() {
        Map<MaterialRole, RoleEntry> r = new EnumMap<>(MaterialRole.class);
        r.put(MaterialRole.MASONRY_MAIN, new RoleEntry(List.of(sandstone, cutSandstone, chiseledSandstone), List.of(sandstone, gravel, redSandstone)));
        r.put(MaterialRole.MASONRY_TRIM, new RoleEntry(List.of(smoothSandstone, cutSandstone), List.of(sandstone)));
        r.put(MaterialRole.MASONRY_FINE, new RoleEntry(List.of(smoothSandstone, calcite), List.of(cutSandstone)));
        r.put(MaterialRole.RIB, new RoleEntry(List.of(smoothSandstone), List.of(sandstone)));
        r.put(MaterialRole.TRACERY, new RoleEntry(List.of(smoothSandstone, sandstone), List.of(sandstone)));
        r.put(MaterialRole.FLOOR, new RoleEntry(List.of(cutSandstone, smoothSandstone), List.of(sandstone, sand)));
        r.put(MaterialRole.FLOOR_WORN, new RoleEntry(List.of(sand, gravel, sandstone)));
        r.put(MaterialRole.ROOF, new RoleEntry(List.of(redSandstone), List.of(redSandstone, gravel)));
        r.put(MaterialRole.TIMBER, new RoleEntry(List.of(oak, spruce), List.of(oak, strippedSpruceLog)));
        r.put(MaterialRole.GLASS_A, mosaic("white", 4, "yellow", 2, "orange", 1, "light_blue", 1));
        r.put(MaterialRole.GLASS_B, mosaic("orange", 3, "yellow", 2, "red", 2, "light_blue", 1));
        r.put(MaterialRole.GLASS_C, glass("yellow"));
        r.put(MaterialRole.GLASS_D, glass("brown"));
        r.put(MaterialRole.GLASS_E, glass("red"));
        r.put(MaterialRole.RUBBLE_STONE, new RoleEntry(List.of(sandstone, sand, gravel)));
        r.put(MaterialRole.RUBBLE_TIMBER, new RoleEntry(List.of(oak, spruceLog)));
        r.put(MaterialRole.RUBBLE_TILE, new RoleEntry(List.of(redSandstone, gravel)));
        r.put(MaterialRole.SOIL, new RoleEntry(List.of(sand, coarseDirt), List.of(coarseDirt)));
        r.put(MaterialRole.SUBSOIL, new RoleEntry(List.of(sand, gravel, coarseDirt), List.of(gravel, sand)));
        r.put(MaterialRole.TURF, new RoleEntry(List.of(sand, coarseDirt), List.of(coarseDirt, gravel)));
        r.put(MaterialRole.ROCK, new RoleEntry(List.of(sandstone, redSandstone, gravel), List.of(sandstone, gravel)));
        r.put(MaterialRole.SAND, new RoleEntry(List.of(sand), List.of(sand, gravel)));
        r.put(MaterialRole.PLANT_IVY, new RoleEntry(List.of(vine, glowLichen)));
        r.put(MaterialRole.PLANT_GROUND, new RoleEntry(List.of(deadBush, shortGrass)));
        r.put(MaterialRole.PLANT_MOSS, new RoleEntry(List.of(moss)));
        r.put(MaterialRole.PLANT_CANOPY, new RoleEntry(List.of(oakLeaves)));
        r.put(MaterialRole.PLANT_TRUNK, new RoleEntry(List.of(oakLog, birchLog)));
        r.put(MaterialRole.PLANT_TRUNK_DEAD, new RoleEntry(List.of(strippedOakLog)));
        r.put(MaterialRole.PLANT_FLOATING, new RoleEntry(List.of(lilyPad)));
        r.put(MaterialRole.PLANT_REED, new RoleEntry(List.of(sugarCane)));
        r.put(MaterialRole.PLANT_ALGAE, new RoleEntry(List.of(seagrass)));
        r.put(MaterialRole.CLUTTER_CRATE, new RoleEntry(List.of(craftingTable, fletchingTable, hayBlock), List.of(hayBlock, composter)));
        r.put(MaterialRole.CLUTTER_FURNITURE, new RoleEntry(List.of(oak, spruce), List.of(oak)));
        hardware(r);
        r.put(MaterialRole.LIGHT, new RoleEntry(List.of(soulLantern, lantern)));
        r.put(MaterialRole.DEBRIS_ROOF, new RoleEntry(List.of(redSandstone, sandstone)));
        r.put(MaterialRole.DEBRIS_STONE, new RoleEntry(List.of(sandstone, cutSandstone)));
        r.put(MaterialRole.DEBRIS_ORGANIC, new RoleEntry(List.of(oak, spruce)));
        return new Palette("sandstone", "Sandstone", r);
    }

    private static Palette blackstone() {
        Map<MaterialRole, RoleEntry> r = new EnumMap<>(MaterialRole.class);
        r.put(MaterialRole.MASONRY_MAIN, new RoleEntry(List.of(polishedBlackstoneBrick, crackedPolishedBlackstoneBrick, blackstone), List.of(blackstone, crackedPolishedBlackstoneBrick, cobbledDeepslate)));
        r.put(MaterialRole.MASONRY_TRIM, new RoleEntry(List.of(polishedBlackstone, basalt), List.of(blackstone)));
        r.put(MaterialRole.MASONRY_FINE, new RoleEntry(List.of(polishedBlackstone, basalt), List.of(blackstone)));
        r.put(MaterialRole.RIB, new RoleEntry(List.of(basalt), List.of(polishedBlackstone)));
        r.put(MaterialRole.TRACERY, new RoleEntry(List.of(polishedBlackstone), List.of(blackstone)));
        r.put(MaterialRole.FLOOR, new RoleEntry(List.of(polishedBlackstone, blackstone), List.of(blackstone, gravel)));
        r.put(MaterialRole.FLOOR_WORN, new RoleEntry(List.of(blackstone, gravel, cobbledDeepslate)));
        r.put(MaterialRole.ROOF, new RoleEntry(List.of(deepslateTile), List.of(crackedDeepslateTile)));
        r.put(MaterialRole.TIMBER, new RoleEntry(List.of(spruce, spruceLog), List.of(spruce, strippedSpruceLog)));
        r.put(MaterialRole.GLASS_A, mosaic("red", 4, "orange", 2, "black", 1, "purple", 1));
        r.put(MaterialRole.GLASS_B, mosaic("red", 3, "orange", 2, "yellow", 1, "purple", 2));
        r.put(MaterialRole.GLASS_C, glass("black"));
        r.put(MaterialRole.GLASS_D, glass("purple"));
        r.put(MaterialRole.GLASS_E, glass("gray"));
        r.put(MaterialRole.RUBBLE_STONE, new RoleEntry(List.of(blackstone, basalt, gravel)));
        r.put(MaterialRole.RUBBLE_TIMBER, new RoleEntry(List.of(spruceLog)));
        r.put(MaterialRole.RUBBLE_TILE, new RoleEntry(List.of(crackedDeepslateTile, blackstone)));
        r.put(MaterialRole.SOIL, new RoleEntry(List.of(coarseDirt, rootedDirt), List.of(podzol)));
        r.put(MaterialRole.SUBSOIL, new RoleEntry(List.of(gravel, coarseDirt, tuff), List.of(gravel, basalt)));
        r.put(MaterialRole.TURF, new RoleEntry(List.of(coarseDirt, gravel), List.of(gravel, basalt)));
        r.put(MaterialRole.ROCK, new RoleEntry(List.of(blackstone, basalt, tuff), List.of(blackstone, gravel)));
        r.put(MaterialRole.SAND, new RoleEntry(List.of(gravel, sand), List.of(gravel, coarseDirt)));
        r.put(MaterialRole.PLANT_IVY, new RoleEntry(List.of(vine, glowLichen)));
        r.put(MaterialRole.PLANT_GROUND, new RoleEntry(List.of(deadBush, fern)));
        r.put(MaterialRole.PLANT_MOSS, new RoleEntry(List.of(moss)));
        r.put(MaterialRole.PLANT_CANOPY, new RoleEntry(List.of(oakLeaves)));
        r.put(MaterialRole.PLANT_TRUNK, new RoleEntry(List.of(spruceLog, darkOakLog)));
        r.put(MaterialRole.PLANT_TRUNK_DEAD, new RoleEntry(List.of(strippedSpruceLog, strippedOakLog)));
        r.put(MaterialRole.PLANT_FLOATING, new RoleEntry(List.of(lilyPad)));
        r.put(MaterialRole.PLANT_REED, new RoleEntry(List.of(sugarCane)));
        r.put(MaterialRole.PLANT_ALGAE, new RoleEntry(List.of(seagrass)));
        r.put(MaterialRole.CLUTTER_CRATE, new RoleEntry(List.of(smithingTable, craftingTable, cartographyTable), List.of(composter, smithingTable)));
        r.put(MaterialRole.CLUTTER_FURNITURE, new RoleEntry(List.of(spruce, darkOak), List.of(spruce)));
        hardware(r);
        r.put(MaterialRole.LIGHT, new RoleEntry(List.of(soulLantern)));
        r.put(MaterialRole.DEBRIS_ROOF, new RoleEntry(List.of(crackedDeepslateTile, blackstone)));
        r.put(MaterialRole.DEBRIS_STONE, new RoleEntry(List.of(blackstone, polishedBlackstone)));
        r.put(MaterialRole.DEBRIS_ORGANIC, new RoleEntry(List.of(spruce, darkOak)));
        return new Palette("blackstone-basalt", "Blackstone and Basalt", r);
    }

    private static Palette vernacular() {
        Map<MaterialRole, RoleEntry> r = new EnumMap<>(MaterialRole.class);
        r.put(MaterialRole.MASONRY_MAIN, new RoleEntry(List.of(cobble, mossyCobble, andesite), List.of(mossyCobble, cobble, gravel)));
        r.put(MaterialRole.MASONRY_TRIM, new RoleEntry(List.of(stoneBrick, granite), List.of(mossyStoneBrick, cobble)));
        r.put(MaterialRole.MASONRY_FINE, new RoleEntry(List.of(smoothStone, calcite), List.of(andesite, cobble)));
        r.put(MaterialRole.INFILL, new RoleEntry(List.of(whiteTerracotta, smoothQuartz, calcite), List.of(lightGrayTerracotta, clay, whiteTerracotta)));
        r.put(MaterialRole.ROOF, new RoleEntry(List.of(bricks, mudBricks), List.of(mudBricks, packedMud)));
        r.put(MaterialRole.THATCH, new RoleEntry(List.of(hayBlock), List.of(hayBlock, moss)));
        r.put(MaterialRole.TIMBER, new RoleEntry(List.of(darkOakLog, darkOak, strippedDarkOakLog), List.of(darkOakLog, spruceLog)));
        r.put(MaterialRole.FLOOR, new RoleEntry(List.of(oak, spruce), List.of(spruce, oakLog)));
        r.put(MaterialRole.FLOOR_WORN, new RoleEntry(List.of(cobble, gravel, coarseDirt), List.of(gravel, podzol)));
        r.put(MaterialRole.GLASS_A, new RoleEntry(List.of(mat("minecraft:glass", Map.of(BlockForm.PANE, "minecraft:glass_pane"), 1)),
                List.of(mat("minecraft:brown_stained_glass", Map.of(BlockForm.PANE, "minecraft:brown_stained_glass_pane"), 1))));
        r.put(MaterialRole.SOIL, new RoleEntry(List.of(dirt, coarseDirt, rootedDirt), List.of(podzol, coarseDirt)));
        r.put(MaterialRole.SUBSOIL, new RoleEntry(List.of(coarseDirt, gravel, clay), List.of(gravel, coarseDirt)));
        r.put(MaterialRole.TURF, new RoleEntry(List.of(grass), List.of(podzol, moss)));
        r.put(MaterialRole.ROCK, new RoleEntry(List.of(stone, granite, andesite), List.of(cobble, gravel)));
        r.put(MaterialRole.SAND, new RoleEntry(List.of(sand, gravel), List.of(gravel, clay)));
        r.put(MaterialRole.PLANT_IVY, new RoleEntry(List.of(vine, glowLichen)));
        r.put(MaterialRole.PLANT_GROUND, new RoleEntry(List.of(shortGrass, fern)));
        r.put(MaterialRole.PLANT_MOSS, new RoleEntry(List.of(moss)));
        r.put(MaterialRole.PLANT_CANOPY, new RoleEntry(List.of(oakLeaves, birchLeaves, azalea)));
        r.put(MaterialRole.PLANT_TRUNK, new RoleEntry(List.of(oakLog, birchLog)));
        r.put(MaterialRole.PLANT_TRUNK_DEAD, new RoleEntry(List.of(strippedOakLog)));
        r.put(MaterialRole.PLANT_FLOATING, new RoleEntry(List.of(lilyPad)));
        r.put(MaterialRole.PLANT_REED, new RoleEntry(List.of(sugarCane)));
        r.put(MaterialRole.PLANT_ALGAE, new RoleEntry(List.of(seagrass)));
        r.put(MaterialRole.CLUTTER_CRATE, new RoleEntry(List.of(craftingTable, composter, hayBlock, fletchingTable), List.of(composter, hayBlock)));
        r.put(MaterialRole.CLUTTER_FURNITURE, new RoleEntry(List.of(oak, birch, spruce), List.of(spruce, oak)));
        hardware(r);
        r.put(MaterialRole.LIGHT, new RoleEntry(List.of(lantern)));
        return new Palette("vernacular", "Vernacular", r);
    }

    private static final Map<String, Palette> BY_ID = new LinkedHashMap<>();

    static {
        for (Palette p : List.of(limestone(), deepslate(), sandstone(), blackstone(), vernacular())) BY_ID.put(p.id, p);
    }

    /** Every shipped palette, in Capella's order. */
    public static List<Palette> builtin() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public static Palette get(String id) {
        Palette p = BY_ID.get(id);
        if (p == null) throw new IllegalArgumentException("unknown palette '" + id + "'; have " + String.join(", ", BY_ID.keySet()));
        return p;
    }
}
