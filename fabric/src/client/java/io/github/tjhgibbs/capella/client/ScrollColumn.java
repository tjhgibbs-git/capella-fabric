package io.github.tjhgibbs.capella.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A vertical list of widgets and section headers inside a fixed box, scrolled with the mouse
 * wheel. Widgets stay ordinary screen children (so focus, narration and tooling see them); the
 * column only positions them and hides those outside the box.
 */
final class ScrollColumn {
    static final int SCROLLBAR = 4;

    private sealed interface Entry permits WidgetEntry, HeaderEntry {
        int height();
    }

    private record WidgetEntry(AbstractWidget widget, int height) implements Entry {
    }

    private record HeaderEntry(Component text, int height) implements Entry {
    }

    private final List<Entry> entries = new ArrayList<>();
    private int x, y, w, h;
    private int scroll;

    void bounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    void clear() {
        entries.clear();
    }

    /** Width available to widgets (room is kept for the scrollbar). */
    int innerWidth() {
        return w - SCROLLBAR - 2;
    }

    int x() {
        return x;
    }

    void add(AbstractWidget widget, int rowHeight) {
        entries.add(new WidgetEntry(widget, rowHeight));
    }

    void header(Component text) {
        entries.add(new HeaderEntry(text, 13));
    }

    int scroll() {
        return scroll;
    }

    void scroll(int value) {
        scroll = Math.max(0, Math.min(value, maxScroll()));
    }

    private int contentHeight() {
        int sum = 0;
        for (Entry e : entries) {
            sum += e.height();
        }
        return sum;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - h);
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    boolean mouseScrolled(double amount) {
        if (maxScroll() == 0) {
            return false;
        }
        scroll(scroll - (int) Math.round(amount * 20));
        layout();
        return true;
    }

    /** Positions widgets for the current scroll; hides any that are not wholly inside the box. */
    void layout() {
        scroll(scroll);
        int cy = y - scroll;
        for (Entry e : entries) {
            if (e instanceof WidgetEntry we) {
                we.widget().setX(x);
                we.widget().setY(cy);
                we.widget().visible = cy >= y && cy + we.widget().getHeight() <= y + h;
            }
            cy += e.height();
        }
    }

    /** Draws headers and the scrollbar; widgets draw themselves. */
    void extract(GuiGraphicsExtractor g, Font font) {
        int cy = y - scroll;
        for (Entry e : entries) {
            if (e instanceof HeaderEntry he && cy >= y && cy + he.height() <= y + h) {
                g.text(font, he.text(), x + 1, cy + 3, 0xFFFFD866);
                g.horizontalLine(x + font.width(he.text()) + 5, x + innerWidth() - 1, cy + 7, 0x40FFFFFF);
            }
            cy += e.height();
        }
        int max = maxScroll();
        if (max > 0) {
            int track = h;
            int thumb = Math.max(12, track * h / (h + max));
            int ty = y + (track - thumb) * scroll / max;
            int sx = x + w - SCROLLBAR;
            g.fill(sx, y, sx + SCROLLBAR, y + h, 0x40000000);
            g.fill(sx, ty, sx + SCROLLBAR, ty + thumb, 0xFF808080);
            g.fill(sx, ty, sx + SCROLLBAR - 1, ty + thumb - 1, 0xFFC0C0C0);
        }
    }
}
