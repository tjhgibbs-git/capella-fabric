package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.hash.Hash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Values printed by Capella's hash.ts; the port must agree bit for bit. */
class HashTest {
    @Test
    void agreesWithTheTypeScript() {
        assertEquals(337960743L, Hash.hashU32(8814));
        assertEquals(4210056115L, Hash.hashU32(1, "tower", 3));
        assertEquals(4225709822L, Hash.hashU32(-5, 2.5, "x"));
        assertEquals(0.23507357155904174, Hash.hash(7, "a", -1));
        assertEquals(2, Hash.hashInt(0, 9, 42, "pier", 3, "b"));
        assertEquals(0.219675035349427, Hash.noise2(3, "w", 10.5, -3.25, 7));
        assertEquals(0.3623819116340883, Hash.noise3(9, "stone", 1, 2, 3, 4));
        assertEquals(145241606L, Hash.hashU32(0.1, 1e10));
    }
}
