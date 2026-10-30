package io.github.tjhgibbs.capella.mod;

import io.github.tjhgibbs.capella.core.GeneratedBuilding;
import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.params.BuildingParams;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Building generation off the game threads. A large cathedral takes a second or more; neither
 * the server tick nor the render thread should wait on it.
 */
public final class Generation {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(
            Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1)), r -> {
                Thread t = new Thread(r, "Capella generator");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });

    private Generation() {
    }

    public static CompletableFuture<GeneratedBuilding> generate(BuildingParams params) {
        return generate(params, 0);
    }

    /** {@code turns}: quarter turns clockwise from the nave running east (see {@code BuildingGenerator}). */
    public static CompletableFuture<GeneratedBuilding> generate(BuildingParams params, int turns) {
        BuildingParams copy = params.copy();
        return CompletableFuture.supplyAsync(() -> BuildingGenerator.generate(copy, turns), POOL);
    }
}
