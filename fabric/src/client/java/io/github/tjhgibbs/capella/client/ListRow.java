package io.github.tjhgibbs.capella.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** A flat, selectable list entry (the preset list), drawn like vanilla's world/server lists. */
final class ListRow extends AbstractButton {
    static final int HEIGHT = 16;

    private final Runnable onPress;
    private final BooleanSupplier selected;

    ListRow(Component label, int width, BooleanSupplier selected, Runnable onPress) {
        super(0, 0, width, HEIGHT - 1, label);
        this.selected = selected;
        this.onPress = onPress;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean sel = selected.getAsBoolean();
        if (sel) {
            g.fill(x, y, x + w, y + h, 0xFF000000);
            g.outline(x, y, w, h, 0xFFFFFFFF);
        } else if (isHoveredOrFocused()) {
            g.fill(x, y, x + w, y + h, 0x40FFFFFF);
        }
        Font font = Minecraft.getInstance().font;
        String text = getMessage().getString();
        if (font.width(text) > w - 8) {
            text = font.plainSubstrByWidth(text, w - 8 - font.width("…")) + "…";
        }
        g.text(font, text, x + 4, y + (h - 8) / 2, sel || isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFB8B8B8);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
