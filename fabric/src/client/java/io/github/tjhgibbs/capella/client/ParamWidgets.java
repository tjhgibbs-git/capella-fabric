package io.github.tjhgibbs.capella.client;

import io.github.tjhgibbs.capella.core.params.Param;
import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.params.Params;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Builds a vanilla widget for any {@link Param}, so new generator settings appear in the GUI
 * without screen code: sliders for numbers, cycle buttons for enums, booleans and small integer
 * ranges (0-2 aisles reads better as a button than as a three-stop slider).
 */
final class ParamWidgets {
    /** Integer settings with this many steps or fewer get a cycle button, not a slider. */
    private static final int SMALL_RANGE = 3;

    private ParamWidgets() {
    }

    static Component label(Param<?> p) {
        return Component.translatableWithFallback("capella.param." + p.key(), p.label());
    }

    static AbstractWidget create(Param<?> param, BuildingParams params, Font font, int x, int y, int w, int h, Runnable changed) {
        return switch (param) {
            case Param.Num n -> new NumSlider(n, params, x, y, w, h, changed);
            case Param.Int i when i.max() - i.min() <= SMALL_RANGE -> intCycle(i, params, x, y, w, h, changed);
            case Param.Int i -> new IntSlider(i, Params.range(i, params.get(Params.TYPE)), params, x, y, w, h, changed);
            case Param.Choice<?> c -> choice(c, params, x, y, w, h, changed);
            case Param.Bool b -> CycleButton.onOffBuilder(params.bool(b))
                    .create(x, y, w, h, label(b), (btn, v) -> {
                        params.set(b, v);
                        changed.run();
                    });
        };
    }

    private static <E extends Enum<E>> AbstractWidget choice(Param.Choice<E> c, BuildingParams params, int x, int y, int w, int h, Runnable changed) {
        return CycleButton.builder((E v) -> Component.translatableWithFallback(
                        "capella.value." + c.key() + "." + v.name().toLowerCase(Locale.ROOT), pretty(v.name())), params.get(c))
                .withValues(c.values())
                .create(x, y, w, h, label(c), (btn, v) -> {
                    params.set(c, v);
                    changed.run();
                });
    }

    static String pretty(String enumName) {
        String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static AbstractWidget intCycle(Param.Int p, BuildingParams params, int x, int y, int w, int h, Runnable changed) {
        Integer[] values = new Integer[p.max() - p.min() + 1];
        for (int k = 0; k < values.length; k++) {
            values[k] = p.min() + k;
        }
        return CycleButton.builder((Integer v) -> Component.literal(Integer.toString(v)), params.integer(p))
                .withValues(values)
                .create(x, y, w, h, label(p), (btn, v) -> {
                    params.set(p, v);
                    changed.run();
                });
    }

    /** 0..1 settings read as percentages; anything else as a plain number. */
    static String format(Param.Num p, double v) {
        if (p.min() >= -1 && p.max() <= 1) {
            return Math.round(v * 100) + "%";
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private abstract static class Slider extends AbstractSliderButton {
        final BuildingParams params;
        final Runnable changed;

        Slider(int x, int y, int w, int h, double value, BuildingParams params, Runnable changed) {
            super(x, y, w, h, Component.empty(), value);
            this.params = params;
            this.changed = changed;
        }
    }

    private static final class NumSlider extends Slider {
        private final Param.Num p;

        NumSlider(Param.Num p, BuildingParams params, int x, int y, int w, int h, Runnable changed) {
            super(x, y, w, h, (params.num(p) - p.min()) / (p.max() - p.min()), params, changed);
            this.p = p;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double v = p.sanitize(p.min() + value * (p.max() - p.min()));
            setMessage(Component.empty().append(label(p)).append(": " + format(p, v)));
        }

        @Override
        protected void applyValue() {
            params.set(p, p.min() + value * (p.max() - p.min()));
            changed.run();
        }
    }

    /** Sizes slide over the building type's range, not the param's whole one. */
    private static final class IntSlider extends Slider {
        private final Param.Int p;
        private final int min, max;

        IntSlider(Param.Int p, int[] range, BuildingParams params, int x, int y, int w, int h, Runnable changed) {
            super(x, y, w, h, Math.clamp((params.integer(p) - range[0]) / (double) (range[1] - range[0]), 0, 1), params, changed);
            this.p = p;
            this.min = range[0];
            this.max = range[1];
            updateMessage();
        }

        private int current() {
            return (int) Math.round(min + value * (max - min));
        }

        @Override
        protected void updateMessage() {
            boolean blocks = p == Params.LENGTH || p == Params.WIDTH || p == Params.HEIGHT;
            setMessage(Component.empty().append(label(p)).append(": " + current() + (blocks ? " blocks" : "")));
        }

        @Override
        protected void applyValue() {
            params.set(p, current());
            changed.run();
        }
    }
}
