package io.github.tjhgibbs.capella.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.tjhgibbs.capella.mod.CapellaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * One preview image as a GPU texture. Pixels come from core's software renderer (ARGB); this
 * class only uploads and draws them. Each instance registers its own texture id, so several
 * (views, variation tiles) can be alive at once. Render thread only.
 */
final class PreviewTexture implements AutoCloseable {
    private static final AtomicInteger NEXT = new AtomicInteger();

    private final Identifier id = Identifier.fromNamespaceAndPath(CapellaMod.MOD_ID, "dynamic/preview_" + NEXT.getAndIncrement());
    private DynamicTexture texture;
    private int width, height;

    boolean ready() {
        return texture != null;
    }

    void upload(int[] argb, int w, int h) {
        if (texture == null || w != width || h != height) {
            close();
            texture = new DynamicTexture(() -> "capella preview", new NativeImage(w, h, false));
            Minecraft.getInstance().getTextureManager().register(id, texture);
            width = w;
            height = h;
        }
        NativeImage img = texture.getPixels();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // setPixel takes ARGB (setPixelABGR is the other order).
                img.setPixel(x, y, argb[y * w + x]);
            }
        }
        texture.upload();
    }

    /** Draws the image into the box, keeping its aspect ratio and centring it. */
    void draw(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (texture == null) {
            return;
        }
        double s = Math.min(w / (double) width, h / (double) height);
        int dw = (int) Math.round(width * s), dh = (int) Math.round(height * s);
        g.blit(RenderPipelines.GUI_TEXTURED, id, x + (w - dw) / 2, y + (h - dh) / 2, 0, 0, dw, dh,
                width, height, width, height);
    }

    @Override
    public void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
        }
    }
}
