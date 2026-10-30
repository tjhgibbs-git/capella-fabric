package io.github.tjhgibbs.capella.core.params;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A full set of generator settings plus the seed. Values not set explicitly fall back to the
 * param's default. Generation is a pure function of a BuildingParams, which is why the client can
 * preview a building and the server can rebuild exactly the same one from {@link #encode()}.
 */
public final class BuildingParams {
    private final Map<String, Object> values = new LinkedHashMap<>();
    private long seed;

    public BuildingParams() {
    }

    public BuildingParams copy() {
        BuildingParams p = new BuildingParams();
        p.values.putAll(values);
        p.seed = seed;
        return p;
    }

    public long seed() {
        return seed;
    }

    public BuildingParams seed(long seed) {
        this.seed = seed;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Param<T> p) {
        Object v = values.get(p.key());
        return v == null ? p.defaultValue() : (T) v;
    }

    public <T> BuildingParams set(Param<T> p, T value) {
        values.put(p.key(), p.sanitize(value));
        return this;
    }

    public boolean isSet(Param<?> p) {
        return values.containsKey(p.key());
    }

    public BuildingParams reset(Param<?> p) {
        values.remove(p.key());
        return this;
    }

    public double num(Param.Num p) {
        return get(p);
    }

    public int integer(Param.Int p) {
        return get(p);
    }

    public boolean bool(Param.Bool p) {
        return get(p);
    }

    /** Sets a param from its string form; used by commands and decoding. */
    public <T> BuildingParams setRaw(Param<T> p, String raw) {
        return set(p, p.parse(raw));
    }

    /**
     * Stable text form: {@code seed=123;nave_bays=7;aisles=1;...}. Only explicitly set values
     * are written, so an encoding stays valid when new params are added later.
     */
    public String encode() {
        StringBuilder sb = new StringBuilder("seed=").append(seed);
        for (Map.Entry<String, Object> e : values.entrySet()) {
            sb.append(';').append(e.getKey()).append('=').append(formatUnchecked(Params.byKey(e.getKey()), e.getValue()));
        }
        return sb.toString();
    }

    /**
     * Inverse of {@link #encode()}. Unknown keys are skipped (forward compatibility); malformed
     * values for known keys throw IllegalArgumentException.
     */
    public static BuildingParams decode(String text) {
        BuildingParams p = new BuildingParams();
        if (text == null || text.isBlank()) {
            return p;
        }
        for (String part : text.split(";")) {
            if (part.isBlank()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("bad entry: " + part);
            }
            String key = part.substring(0, eq).trim();
            String raw = part.substring(eq + 1);
            if (key.equals("seed")) {
                p.seed = Long.parseLong(raw.trim());
                continue;
            }
            Param<?> param = Params.byKey(key);
            if (param != null) {
                p.setRaw(param, raw);
            }
        }
        return p;
    }

    @SuppressWarnings("unchecked")
    private static <T> String formatUnchecked(Param<T> p, Object v) {
        return p.format((T) v);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BuildingParams t && t.seed == seed && t.values.equals(values);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(seed) * 31 + values.hashCode();
    }

    @Override
    public String toString() {
        return encode();
    }
}
