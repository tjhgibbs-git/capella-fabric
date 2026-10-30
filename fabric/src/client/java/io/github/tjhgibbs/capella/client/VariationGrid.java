package io.github.tjhgibbs.capella.client;

import io.github.tjhgibbs.capella.core.GeneratedBuilding;
import io.github.tjhgibbs.capella.core.params.Param;
import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.preview.BlockColors;
import io.github.tjhgibbs.capella.core.preview.PreviewRenderer;
import io.github.tjhgibbs.capella.mod.Generation;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A handful of variants of the current building, each generated and rendered in the background.
 * A pristine church barely changes with its seed, so a variant changes the plan: {@link Variety}
 * says how far. The stone is never changed; it is the player's choice, not a variation. The
 * screen lets the player adopt one or pick several to save.
 */
final class VariationGrid implements AutoCloseable {
    enum Variety {
        /** Numbers nudged: a bay more or less, a little wider, a tower a little taller. */
        SUBTLE(0.06, 1, 0),
        /** Numbers moved further, and about a third of the choices re-picked. */
        WILD(0.16, 2, 0.35);

        final double amount;
        final int steps;
        final double repick;

        Variety(double amount, int steps, double repick) {
            this.amount = amount;
            this.steps = steps;
            this.repick = repick;
        }
    }

    static final class Tile {
        final BuildingParams params;
        final PreviewTexture texture = new PreviewTexture();
        GeneratedBuilding building;
        boolean picked;

        Tile(BuildingParams params) {
            this.params = params;
        }

        boolean ready() {
            return texture.ready();
        }
    }

    private final List<Tile> tiles = new ArrayList<>();
    private int generation;

    List<Tile> tiles() {
        return tiles;
    }

    boolean isEmpty() {
        return tiles.isEmpty();
    }

    List<Tile> picked() {
        return tiles.stream().filter(t -> t.picked).toList();
    }

    /** Replaces the tiles with {@code count} new variants of {@code base}, rendered at w x h pixels. */
    void shuffle(BuildingParams base, Variety variety, int count, int w, int h) {
        close();
        int gen = ++generation;
        Random rng = new Random(ThreadLocalRandom.current().nextLong());
        for (int i = 0; i < count; i++) {
            BuildingParams p = vary(base, variety, rng);
            Tile tile = new Tile(p);
            tiles.add(tile);
            PreviewRenderer.View view = BuildingViews.view(BuildingViews.Angle.ABOVE, 0, w, h);
            Generation.generate(p)
                    .thenApply(t -> new Object[]{t, PreviewRenderer.render(t.volume(), view, BlockColors::rgb)})
                    .thenAccept(r -> Minecraft.getInstance().execute(() -> {
                        if (gen == generation) {
                            tile.building = (GeneratedBuilding) r[0];
                            tile.texture.upload((int[]) r[1], w, h);
                        }
                    }));
        }
    }

    static BuildingParams vary(BuildingParams base, Variety variety, Random rng) {
        BuildingParams p = base.copy().seed(rng.nextLong(1, 1L << 40));
        for (Param<?> param : Params.all()) {
            if (param == Params.STONE) {
                continue;
            }
            switch (param) {
                case Param.Num n -> p.set(n, p.num(n) + rng.nextGaussian() * variety.amount * (n.max() - n.min()));
                // Heights vary relative to themselves; small counts by whole steps.
                case Param.Int i when i.max() - i.min() > 20 ->
                        p.set(i, (int) Math.round(p.integer(i) * (1 + rng.nextGaussian() * variety.amount * 1.5)));
                case Param.Int i -> {
                    if (rng.nextDouble() < 0.5) {
                        p.set(i, p.integer(i) + rng.nextInt(-variety.steps, variety.steps + 1));
                    }
                }
                case Param.Choice<?> c -> {
                    if (rng.nextDouble() < variety.repick) {
                        repick(p, c, rng);
                    }
                }
                case Param.Bool b -> {
                    if (rng.nextDouble() < variety.repick * 0.5) {
                        p.set(b, !p.bool(b));
                    }
                }
            }
        }
        return p;
    }

    private static <E extends Enum<E>> void repick(BuildingParams p, Param.Choice<E> c, Random rng) {
        E[] values = c.values();
        p.set(c, values[rng.nextInt(values.length)]);
    }

    @Override
    public void close() {
        generation++;
        for (Tile t : tiles) {
            t.texture.close();
        }
        tiles.clear();
    }
}
