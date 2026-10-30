package io.github.tjhgibbs.capella.client;

import io.github.tjhgibbs.capella.core.GeneratedBuilding;
import io.github.tjhgibbs.capella.core.preview.BlockColors;
import io.github.tjhgibbs.capella.core.preview.PreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.concurrent.CompletableFuture;

/**
 * The fixed camera angles of one generated building: three elevations (a bird's-eye view, eye
 * level, and nearly straight down to read the plan) times four quarter turns.
 * A view is only rendered when it is asked for; {@link #prefetch()} renders the rest in the
 * background once the player starts looking around, so switching is instant. Everything is
 * released with {@link #close()} when the building changes or the screen closes.
 */
final class BuildingViews implements AutoCloseable {
    enum Angle {
        ABOVE(32), LEVEL(6), PLAN(80);

        final double pitch;

        Angle(double degrees) {
            this.pitch = Math.toRadians(degrees);
        }
    }

    static final int TURNS = 4;
    private static final double BASE_YAW = Math.toRadians(35);

    private final GeneratedBuilding building;
    private final int width, height;
    private final PreviewTexture[] textures = new PreviewTexture[Angle.values().length * TURNS];
    private final boolean[] requested = new boolean[textures.length];
    private boolean closed;

    BuildingViews(GeneratedBuilding building, int width, int height) {
        this.building = building;
        this.width = width;
        this.height = height;
    }

    boolean sized(int w, int h) {
        return w == width && h == height;
    }

    private static int index(Angle a, int turn) {
        return a.ordinal() * TURNS + Math.floorMod(turn, TURNS);
    }

    static PreviewRenderer.View view(Angle a, int turn, int w, int h) {
        return new PreviewRenderer.View(w, h, BASE_YAW + Math.floorMod(turn, TURNS) * Math.PI / 2, a.pitch, 1, 0, 0, false);
    }

    /** Starts rendering a view unless it is done or under way. */
    void request(Angle a, int turn) {
        int i = index(a, turn);
        if (requested[i]) {
            return;
        }
        requested[i] = true;
        PreviewRenderer.View v = view(a, turn, width, height);
        CompletableFuture.supplyAsync(() -> PreviewRenderer.render(building.volume(), v, BlockColors::rgb))
                .thenAccept(px -> Minecraft.getInstance().execute(() -> {
                    if (!closed) {
                        PreviewTexture t = new PreviewTexture();
                        t.upload(px, width, height);
                        textures[i] = t;
                    }
                }));
    }

    /** Renders every view that has not been asked for yet. */
    void prefetch() {
        for (Angle a : Angle.values()) {
            for (int t = 0; t < TURNS; t++) {
                request(a, t);
            }
        }
    }

    boolean ready(Angle a, int turn) {
        return textures[index(a, turn)] != null;
    }

    /** Draws the view, or returns false if it is not rendered yet. */
    boolean draw(GuiGraphicsExtractor g, Angle a, int turn, int x, int y, int w, int h) {
        PreviewTexture t = textures[index(a, turn)];
        if (t == null) {
            return false;
        }
        t.draw(g, x, y, w, h);
        return true;
    }

    @Override
    public void close() {
        closed = true;
        for (int i = 0; i < textures.length; i++) {
            if (textures[i] != null) {
                textures[i].close();
                textures[i] = null;
            }
        }
    }
}
