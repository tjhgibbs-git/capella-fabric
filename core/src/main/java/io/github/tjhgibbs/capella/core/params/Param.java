package io.github.tjhgibbs.capella.core.params;

import java.util.Locale;

/**
 * Declaration of one tunable generator setting. The GUI builds its widgets from these, and
 * {@link BuildingParams} (de)serialises them generically, so adding a setting is one line in
 * {@link Params} plus wherever the generator reads it.
 */
public sealed interface Param<T> permits Param.Num, Param.Int, Param.Choice, Param.Bool {

    String key();

    /** Translation key suffix / English fallback label. */
    String label();

    Category category();

    T defaultValue();

    /** Parses {@link #format} output; throws IllegalArgumentException on bad input. */
    T parse(String raw);

    String format(T value);

    /** Clamps or otherwise sanitises a value before it is stored. */
    T sanitize(T value);

    enum Category {
        PLAN, ELEVATION, TOWERS, STYLE, MATERIALS
    }

    record Num(String key, String label, Category category, double min, double max, double step, Double defaultValue)
            implements Param<Double> {
        @Override
        public Double parse(String raw) {
            return Double.parseDouble(raw.trim());
        }

        @Override
        public String format(Double value) {
            return Double.toString(value);
        }

        @Override
        public Double sanitize(Double value) {
            double v = Math.max(min, Math.min(max, value));
            return step > 0 ? Math.round(v / step) * step : v;
        }
    }

    record Int(String key, String label, Category category, int min, int max, Integer defaultValue)
            implements Param<Integer> {
        @Override
        public Integer parse(String raw) {
            return Integer.parseInt(raw.trim());
        }

        @Override
        public String format(Integer value) {
            return Integer.toString(value);
        }

        @Override
        public Integer sanitize(Integer value) {
            return Math.max(min, Math.min(max, value));
        }
    }

    record Choice<E extends Enum<E>>(String key, String label, Category category, Class<E> type, E defaultValue)
            implements Param<E> {
        @Override
        public E parse(String raw) {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        }

        @Override
        public String format(E value) {
            return value.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public E sanitize(E value) {
            return value;
        }

        public E[] values() {
            return type.getEnumConstants();
        }
    }

    record Bool(String key, String label, Category category, Boolean defaultValue) implements Param<Boolean> {
        @Override
        public Boolean parse(String raw) {
            String s = raw.trim();
            if (s.equalsIgnoreCase("true")) {
                return true;
            }
            if (s.equalsIgnoreCase("false")) {
                return false;
            }
            throw new IllegalArgumentException("not a boolean: " + raw);
        }

        @Override
        public String format(Boolean value) {
            return value.toString();
        }

        @Override
        public Boolean sanitize(Boolean value) {
            return value;
        }
    }
}
