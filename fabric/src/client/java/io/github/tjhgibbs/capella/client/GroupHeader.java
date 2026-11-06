package io.github.tjhgibbs.capella.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * A collection's heading in the preset list: click it to fold the collection away or open it.
 * Folded, it shows how many presets it holds; either way it draws like the column's other headers.
 */
final class GroupHeader extends AbstractButton {
    static final int HEIGHT = 15;

    private final Runnable onPress;
    private final BooleanSupplier open;
    private final int count;

    GroupHeader(Component label, int count, int width, BooleanSupplier open, Runnable onPress) {
        super(0, 0, width, HEIGHT - 1, label);
        this.count = count;
        this.open = open;
        this.onPress = onPress;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hot = isHoveredOrFocused();
        if (hot) {
            g.fill(x, y, x + w, y + h, 0x30FFFFFF);
        }
        Font font = Minecraft.getInstance().font;
        boolean isOpen = open.getAsBoolean();
        int ty = y + (h - 8) / 2;
        int colour = hot ? 0xFFFFEAA0 : 0xFFFFD866;
        g.text(font, isOpen ? "▾" : "▸", x + 1, ty, colour);
        int tx = x + 9;
        g.text(font, getMessage(), tx, ty, colour);
        int end = tx + font.width(getMessage()) + 4;
        if (!isOpen) {
            String n = Integer.toString(count);
            int nx = x + w - 3 - font.width(n);
            g.text(font, n, nx, ty, 0xFF9AA4AE);
            g.horizontalLine(end, nx - 4, y + h / 2, 0x40FFFFFF);
        } else {
            g.horizontalLine(end, x + w - 2, y + h / 2, 0x40FFFFFF);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
