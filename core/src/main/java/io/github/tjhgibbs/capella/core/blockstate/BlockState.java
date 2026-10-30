package io.github.tjhgibbs.capella.core.blockstate;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * A namespaced block id and its properties (Capella {@code BlockState}). Properties are kept
 * sorted, so {@link #key()} is the canonical {@code name[k=v,...]} string the volume interns by.
 */
public final class BlockState {
    public final String name;
    public final Map<String, String> props;
    private final String key;

    public BlockState(String name, Map<String, String> props) {
        this.name = name;
        this.props = Collections.unmodifiableMap(new TreeMap<>(props));
        this.key = computeKey();
    }

    public BlockState(String name) {
        this(name, Map.of());
    }

    private String computeKey() {
        if (props.isEmpty()) return name;
        StringBuilder out = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append(e.getKey()).append('=').append(e.getValue());
        }
        return out.append(']').toString();
    }

    /** {@code stateKey}: the canonical string form. */
    public String key() {
        return key;
    }

    public String prop(String k) {
        return props.get(k);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BlockState b && b.key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key;
    }
}
