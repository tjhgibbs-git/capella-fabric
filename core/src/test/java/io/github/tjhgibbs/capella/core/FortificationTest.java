package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.raster.Volume;
import io.github.tjhgibbs.capella.core.small.Fortification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FortificationTest {
    private static final List<String> TOWERS = List.of("squarekeep", "roundwatchtower", "gothicturret");
    private static final List<String> WALLS = List.of("curtainwall", "arcadedwall", "plainwall");

    @Test
    void laddersHaveBackingAndClearExitsAtEverySizeAndRotation() {
        for (String id : TOWERS) for (int width : new int[]{13, 14, 35}) for (int height : new int[]{12, 96}) {
            var p = Presets.get(id).params(7).set(Params.WIDTH, width).set(Params.HEIGHT, height).set(Params.ACCESS, Params.Access.LADDER);
            Volume v = Fortification.build(p, 0);
            for (int y = 0; y < height; y++) {
                assertTrue(v.keyAt(3, y, 2).contains("ladder"), id + ": " + y);
                assertFalse(v.isAirAt(2, y, 2), id + " backing " + y);
            }
            for (int y = 0; y < 3; y++) assertTrue(v.isAirAt(1, y, 0), id + " doorway");
            assertFalse(v.isAirAt(3, height - 1, 3), id + " exit floor");
            assertTrue(v.isAirAt(3, height, 3), id + " exit feet");
            assertTrue(v.isAirAt(3, height + 1, 3), id + " exit head");
            for (int turn = 0; turn < 4; turn++) {
                Volume rotated = Fortification.build(p, turn);
                int x = 3, z = 2;
                for (int t = 0; t < turn; t++) { int nextX = -z; z = x; x = nextX; }
                String[] facing = {"east", "south", "west", "north"};
                assertEquals(facing[turn], rotated.at(x, 0, z).props.get("facing"));
            }
            Volume off = Fortification.build(p.set(Params.ACCESS, Params.Access.NONE), 0);
            assertFalse(java.util.stream.IntStream.range(0, off.paletteSize()).anyMatch(i -> off.keyOfId(i).contains("ladder")));
        }
    }

    @Test
    void wallWalkHasContinuousDeckGuardsAndNoEndLadders() {
        for (String id : WALLS) for (int width : new int[]{5, 6, 11}) for (int height : new int[]{5, 40}) {
            var p = Presets.get(id).params(1).set(Params.LENGTH, 24).set(Params.WIDTH, width)
                    .set(Params.HEIGHT, height).set(Params.WALKABLE, true);
            Volume v = Fortification.build(p, 0);
            for (int x = 0; x < 24; x++) {
                assertFalse(v.isAirAt(x, height - 1, 0), id + " deck");
                assertTrue(v.isAirAt(x, height, 0), id + " feet");
                assertTrue(v.isAirAt(x, height + 1, 0), id + " head");
                assertFalse(v.isAirAt(x, height, -width / 2), id + " guard");
            }
            for (int y = 0; y < height; y++) assertTrue(v.isAirAt(-1, y, 0), id + " clear modular end");
            assertFalse(java.util.stream.IntStream.range(0, v.paletteSize()).anyMatch(i -> v.keyOfId(i).contains("ladder")));
            assertFalse(Params.applies(Params.ACCESS, p.get(Params.TYPE)));
            Volume noAccess = Fortification.build(p.copy().set(Params.ACCESS, Params.Access.NONE), 0);
            assertTrue(noAccess.isAirAt(-1, 0, 0));
            Volume noWalk = Fortification.build(p.copy().set(Params.WALKABLE, false), 0);
            assertTrue(noWalk.isAirAt(-1, 0, 0));
        }
    }

    @Test
    void arcadesAreOpenBelowTheirSpandrelsAndPlainWallsRemainSolid() {
        Volume arch = Fortification.build(Presets.get("arcadedwall").params(1), 0);
        Volume solid = Fortification.build(Presets.get("curtainwall").params(1), 0);
        assertTrue(arch.isAirAt(4, 2, 0));
        assertFalse(arch.isAirAt(8, 2, 0));
        assertFalse(arch.isAirAt(4, 9, 0));
        assertFalse(solid.isAirAt(4, 2, 0));
    }

    @Test
    void settingsAndEncodingExposeOnlyRelevantControls() {
        for (Params.BuildingType t : Params.BuildingType.values()) {
            for (Param<?> setting : Params.basic(t)) assertTrue(Params.applies(setting, t), t + ": " + setting.key());
            if (!t.fortification()) continue;
            var p = new BuildingParams();
            Params.changeType(p, t);
            p.set(Params.ACCESS, Params.Access.NONE).set(Params.WALKABLE, false).seed(99);
            assertEquals(p, BuildingParams.decode(p.encode()));
            assertFalse(Params.applies(Params.BELFRY, t));
            assertEquals(t.wall() || t.outbuilding(), Params.applies(Params.LENGTH, t));
        }
    }
}
