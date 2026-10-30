package io.github.tjhgibbs.capella.core.export;

import java.util.LinkedHashMap;
import java.util.Map;

/** Splits {@code minecraft:oak_log[axis=y]} into a name and a property map. */
public record BlockStateString(String name, Map<String, String> properties) {
    public static BlockStateString parse(String state) {
        int open = state.indexOf('[');
        if (open < 0) {
            return new BlockStateString(state, Map.of());
        }
        Map<String, String> props = new LinkedHashMap<>();
        String inner = state.substring(open + 1, state.endsWith("]") ? state.length() - 1 : state.length());
        for (String kv : inner.split(",")) {
            int eq = kv.indexOf('=');
            if (eq > 0) {
                props.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
        }
        return new BlockStateString(state.substring(0, open), props);
    }
}
