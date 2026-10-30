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
