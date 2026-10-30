package io.github.tjhgibbs.capella.client;

import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;

/**
 * Asked before "Build here" places something very large: building it block by block in the world
 * is slow and can lag the server, so it suggests saving a .schem to paste with Axiom instead,
 * while still letting the player build it anyway.
 */
final class BigBuildScreen extends Screen {
    /** Buildings whose bounding box holds more than about 120 x 120 x 120 blocks ask first. */
    static final long LIMIT = 120L * 120 * 120;

    private static final int PANEL_W = 300;

    private final Screen parent;
    private final BlockVolume volume;
    private final Runnable build, save;

    static boolean isBig(BlockVolume v) {
        return (long) v.width * v.height * v.length > LIMIT;
    }

    BigBuildScreen(Screen parent, BlockVolume volume, Runnable build, Runnable save) {
        super(Component.translatable("capella.big.title"));
        this.parent = parent;
        this.volume = volume;
        this.build = build;
        this.save = save;
    }

    private int panelW() {
        return Math.min(PANEL_W, width - 20);
    }

    private List<FormattedCharSequence> lines() {
        Component size = Component.translatable("capella.info.size", volume.width, volume.length, volume.height);
        Component msg = Component.translatable("capella.big.message", size, String.format(Locale.ROOT, "%,d", volume.blockCount()));
        return font.split(msg, panelW());
    }

    private int top() {
        return Math.max(10, height / 2 - (lines().size() * 10 + 60) / 2);
    }

    @Override
    protected void init() {
        int w = panelW(), x = (width - w) / 2;
        int y = top() + 20 + lines().size() * 10 + 12;
        int bw = (w - 4) / 3;
        addRenderableWidget(Button.builder(Component.translatable("capella.big.save"), b -> save.run())
                .bounds(x, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("capella.big.build"), b -> build.run())
                .bounds(x + bw + 2, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(x + (bw + 2) * 2, y, w - (bw + 2) * 2, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int y = top();
        g.centeredText(font, title, width / 2, y, 0xFFFFD866);
        y += 20;
        for (FormattedCharSequence line : lines()) {
            g.centeredText(font, line, width / 2, y, 0xFFFFFFFF);
            y += 10;
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
