package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.params.Params.Access;
import io.github.tjhgibbs.capella.core.params.Params.BuildingType;
import io.github.tjhgibbs.capella.core.params.Params.Lighting;
import io.github.tjhgibbs.capella.core.params.Params.Windows;
import io.github.tjhgibbs.capella.core.params.Presets;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Lighting, windows, access, handrails and limestone timber on towers and garrisons. */
class FinishTest {
    private static Map<String, Integer> counts(BuildingParams p) {
        Map<String, Integer> out = new java.util.HashMap<>();
        BuildingGenerator.generate(p, 0).volume().forEachBlock((x, y, z, state) -> out.merge(state, 1, Integer::sum));
        return out;
    }

    private static int sum(Map<String, Integer> c, java.util.function.Predicate<String> name) {
        return c.entrySet().stream().filter(e -> name.test(e.getKey())).mapToInt(Map.Entry::getValue).sum();
    }

    private static String id(String key) {
        int b = key.indexOf('[');
        return b < 0 ? key : key.substring(0, b);
    }

    @Test
    void defaultsAreDimStairsNoHandrailsClearGlass() {
        BuildingParams p = new BuildingParams();
        assertEquals(Lighting.DIM, p.get(Params.LIGHTING));
        assertEquals(Access.STAIRS, p.get(Params.ACCESS));
        assertFalse(p.bool(Params.HANDRAILS));
        assertEquals(Windows.GLASS, p.get(Params.WINDOWS));
    }

    @Test
    void lightingSettings() {
        for (String preset : List.of("barracks", "squarekeep", "parish", "cathedral")) {
            BuildingParams base = Presets.get(preset).params(3);
            var bright = counts(base.copy().set(Params.LIGHTING, Lighting.BRIGHT));
            var dim = counts(base.copy().set(Params.LIGHTING, Lighting.DIM));
            var none = counts(base.copy().set(Params.LIGHTING, Lighting.NONE));
            int lanterns = sum(bright, k -> id(k).equals("minecraft:lantern"));
            assertTrue(lanterns > 0, preset + " has lanterns");
            assertEquals(0, sum(dim, k -> id(k).equals("minecraft:lantern")), preset);
            assertEquals(lanterns, sum(dim, k -> id(k).equals("minecraft:soul_lantern")), preset);
            assertEquals(0, sum(none, k -> id(k).endsWith("lantern")), preset);
            assertEquals(0, sum(none, k -> k.contains("lit=true")), preset + " nothing lit");
            assertEquals(0, sum(dim, k -> id(k).equals("minecraft:campfire")), preset);
            for (String k : bright.keySet()) if (id(k).endsWith("candle") && k.contains("candles=1"))
                assertTrue(dim.containsKey(k), preset + " single candles stay");
            assertTrue(sum(dim, k -> k.contains("candles=4") || k.contains("candles=3")) == 0, preset + " fewer candles");
        }
    }

    @Test
    void windowSettingsOnGlazedTowersAndGarrisons() {
        for (String preset : List.of("barracks", "stables", "gothicturret", "tower", "westtower")) {
            BuildingParams base = Presets.get(preset).params(5);
            assertTrue(Params.applies(Params.WINDOWS, base.get(Params.TYPE)), preset);
            var glass = counts(base.copy().set(Params.WINDOWS, Windows.GLASS));
            int panes = sum(glass, k -> id(k).equals("minecraft:glass_pane") || id(k).equals("minecraft:glass"));
            assertTrue(panes > 0, preset + " has glass");
            assertEquals(0, sum(glass, k -> k.contains("stained_glass")), preset);
            var stained = counts(base.copy().set(Params.WINDOWS, Windows.STAINED));
            assertEquals(panes, sum(stained, k -> k.contains("stained_glass")), preset);
            assertTrue(stained.keySet().stream().filter(k -> k.contains("stained_glass")).map(FinishTest::id).distinct().count() > 1, preset + " mixed colours");
            var bars = counts(base.copy().set(Params.WINDOWS, Windows.IRON_BARS));
            assertEquals(0, sum(bars, k -> k.contains("glass")), preset);
            assertTrue(sum(bars, k -> id(k).equals("minecraft:iron_bars")) >= panes, preset);
            var empty = counts(base.copy().set(Params.WINDOWS, Windows.EMPTY));
            assertEquals(0, sum(empty, k -> k.contains("glass")), preset);
        }
        // Churches keep their glass.
        assertFalse(Params.applies(Params.WINDOWS, BuildingType.PARISH));
        var church = counts(Presets.get("parish").params(5).set(Params.WINDOWS, Windows.EMPTY));
        assertTrue(sum(church, k -> k.contains("glass")) > 0);
    }

    @Test
    void accessAndHandrails() {
        BuildingParams keep = Presets.get("squarekeep").params(2);
        var stairs = counts(keep.copy().set(Params.ACCESS, Access.STAIRS));
        var ladder = counts(keep.copy().set(Params.ACCESS, Access.LADDER));
        var both = counts(keep.copy().set(Params.ACCESS, Access.BOTH));
        int turretLadders = sum(stairs, k -> id(k).equals("minecraft:ladder"));
        assertTrue(sum(ladder, k -> id(k).equals("minecraft:ladder")) > turretLadders, "ladder shaft");
        assertEquals(sum(ladder, k -> id(k).equals("minecraft:ladder")), sum(both, k -> id(k).equals("minecraft:ladder")));
        assertTrue(sum(stairs, k -> k.contains("spruce_stairs") && k.contains("half=bottom")) > sum(ladder, k -> k.contains("spruce_stairs") && k.contains("half=bottom")));
        var rails = counts(keep.copy().set(Params.HANDRAILS, true));
        assertTrue(sum(rails, k -> id(k).endsWith("_fence")) > sum(stairs, k -> id(k).endsWith("_fence")), "handrails");
        var barracks = Presets.get("barracks").params(2).set(Params.HEIGHT, 16);
        assertTrue(sum(counts(barracks.copy().set(Params.HANDRAILS, true)), k -> id(k).endsWith("_fence"))
                > sum(counts(barracks), k -> id(k).endsWith("_fence")), "barracks stairwell rail");
        // Compact towers offer only a ladder or nothing.
        BuildingParams bp = new BuildingParams();
        Params.changeType(bp, BuildingType.SENTRY_TOWER);
        assertEquals(Access.LADDER, bp.get(Params.ACCESS));
        Params.changeType(bp, BuildingType.SQUARE_KEEP);
        assertEquals(Access.STAIRS, bp.get(Params.ACCESS));
        // Every preset's access is one its type offers (the GUI shows the stored value).
        for (var preset : Presets.all()) {
            BuildingParams pp = preset.params(1);
            assertTrue(java.util.Arrays.asList(Params.choices(Params.ACCESS, pp.get(Params.TYPE))).contains(pp.get(Params.ACCESS)), preset.id());
        }
        // The old on/off switch still decodes.
        assertEquals(Access.BOTH, BuildingParams.decode("seed=1;access=true").get(Params.ACCESS));
        assertEquals(Access.NONE, BuildingParams.decode("seed=1;access=false").get(Params.ACCESS));
    }

    @Test
    void limestoneTimberIsSpruce() {
        for (String preset : List.of("squarekeep", "barracks", "stables", "sentrytower", "timberwatchtower", "tower", "westtower", "parish", "cathedral", "priory", "colossal")) {
            var c = counts(Presets.get(preset).params(1).set(Params.STONE, Params.Stone.LIMESTONE));
            assertEquals(0, sum(c, k -> k.contains("dark_oak")), preset);
        }
    }
}
