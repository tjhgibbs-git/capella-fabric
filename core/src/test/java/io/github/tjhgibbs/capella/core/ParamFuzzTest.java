package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.params.Param;
import io.github.tjhgibbs.capella.core.params.Params;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Any combination the GUI can set must build: random settings across every slider and choice. */
class ParamFuzzTest {
    @Test
    void randomSettingsAlwaysBuild() {
        Random r = new Random(20261030);
        for (int i = 0; i < 60; i++) {
            BuildingParams p = new BuildingParams().seed(r.nextLong(1, 1L << 48));
            for (Param<?> param : Params.all()) randomise(p, param, r);
            try {
                GeneratedBuilding b = BuildingGenerator.generate(p, r.nextInt(4));
                assertTrue(b.volume().blockCount() > 1000, p.encode());
            } catch (RuntimeException e) {
                fail("settings " + p.encode() + " threw", e);
            }
        }
    }

    /** Abbey churches inside the GUI's own ranges: the compact section must hold at every size and choice. */
    @Test
    void abbeyChurchesBuildAcrossTheirRanges() {
        Random r = new Random(20261103);
        for (int i = 0; i < 30; i++) {
            BuildingParams p = new BuildingParams().seed(r.nextLong(1, 1L << 48));
            for (Param<?> param : Params.all()) randomise(p, param, r);
            p.set(Params.TYPE, Params.BuildingType.ABBEY);
            for (Param.Int size : java.util.List.of(Params.LENGTH, Params.WIDTH, Params.HEIGHT)) {
                int[] range = Params.range(size, Params.BuildingType.ABBEY);
                p.set(size, range[0] + r.nextInt(range[1] - range[0] + 1));
            }
            try {
                GeneratedBuilding b = BuildingGenerator.generate(p, r.nextInt(4));
                assertTrue(b.volume().blockCount() > 1000, p.encode());
            } catch (RuntimeException e) {
                fail("settings " + p.encode() + " threw", e);
            }
        }
    }

    /** Walls inside the GUI's own ranges: every length, height, tower count and shape, with and without a gate. */
    @Test
    void wallsBuildAcrossTheirRanges() {
        Random r = new Random(20261106);
        for (int i = 0; i < 60; i++) {
            BuildingParams p = new BuildingParams().seed(r.nextLong(1, 1L << 48));
            for (Param<?> param : Params.all()) randomise(p, param, r);
            p.set(Params.TYPE, Params.BuildingType.WALL);
            for (Param.Int size : java.util.List.of(Params.LENGTH, Params.WIDTH, Params.HEIGHT)) {
                int[] range = Params.range(size, Params.BuildingType.WALL);
                p.set(size, range[0] + r.nextInt(range[1] - range[0] + 1));
            }
            Params.snapChoices(p);
            try {
                GeneratedBuilding b = BuildingGenerator.generate(p, r.nextInt(4));
                assertTrue(b.volume().blockCount() > 200, p.encode());
                // The wall runs across the view: never much deeper than its towers, about as long as asked.
                var v = b.volume();
                int along = Math.max(v.width, v.length), across = Math.min(v.width, v.length);
                assertTrue(across <= 2 * Params.range(Params.WIDTH, Params.BuildingType.WALL)[1] + 4, "too deep: " + p.encode());
                assertTrue(along >= p.integer(Params.LENGTH) - 1, "too short: " + p.encode());
            } catch (RuntimeException e) {
                fail("settings " + p.encode() + " threw", e);
            }
        }
    }

    private static <T> void randomise(BuildingParams p, Param<T> param, Random r) {
        Object v = switch (param) {
            case Param.Num n -> n.min() + Math.round(r.nextDouble() * (n.max() - n.min()) / n.step()) * n.step();
            case Param.Int n -> n.min() + r.nextInt(n.max() - n.min() + 1);
            case Param.Choice<?> c -> c.values()[r.nextInt(c.values().length)];
            case Param.Bool b -> r.nextBoolean();
        };
        @SuppressWarnings("unchecked") T t = (T) v;
        p.set(param, param.sanitize(t));
    }
}
