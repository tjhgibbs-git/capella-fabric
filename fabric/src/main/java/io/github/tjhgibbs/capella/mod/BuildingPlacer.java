package io.github.tjhgibbs.capella.mod;

import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Places buildings over several ticks (a cathedral is 10^5 to 10^6 blocks) and keeps a short undo
 * history per player. Blocks are set with complete states and without neighbour updates, so
 * stairs, walls and panes stay exactly as generated. Server thread only.
 */
public final class BuildingPlacer {
    /** Same flags 14erfabric uses for "no physics" edits. */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;
    static final int BLOCKS_PER_TICK = 25_000;
    static final int UNDO_DEPTH = 5;
    private static final String STRUCTURE_VOID = "minecraft:structure_void";

    private record Edit(BlockPos pos, BlockState state) {
    }

    private static final class Job {
        final ServerLevel level;
        final List<Edit> edits;
        final List<Edit> previous;
        final UUID owner;
        final Consumer<Integer> onDone;
        final boolean isUndo;
        int next;

        Job(ServerLevel level, List<Edit> edits, UUID owner, boolean isUndo, Consumer<Integer> onDone) {
            this.level = level;
            this.edits = edits;
            this.previous = new ArrayList<>(isUndo ? 0 : edits.size());
            this.owner = owner;
            this.isUndo = isUndo;
            this.onDone = onDone;
        }
    }

    private static final Deque<Job> QUEUE = new ArrayDeque<>();
    private static final Map<UUID, Deque<Job>> HISTORY = new HashMap<>();

    private BuildingPlacer() {
    }

    /**
     * Queues a building with its origin (the west door's threshold) at {@code base}.
     *
     * <p>Air is written only in columns the building occupies, so the nave is cleared of whatever
     * stood there (grass, trees, a hillside) while the ground round the towers and buttresses is
     * left alone. {@code minecraft:structure_void} is never written. Invalid states are skipped
     * and counted.
     *
     * @return number of block states that could not be parsed (0 normally)
     */
    public static int enqueue(ServerLevel level, BlockVolume v, BlockPos base, UUID owner, Consumer<Integer> onDone) {
        List<Edit> edits = new ArrayList<>(v.blockCount());
        BlockState[] states = new BlockState[v.palette().size()];
        int invalid = 0;
        for (int i = 1; i < states.length; i++) {
            if (v.palette().get(i).equals(STRUCTURE_VOID)) {
                continue;
            }
            states[i] = McBlocks.parse(v.palette().get(i));
            if (states[i] == null) {
                invalid++;
            }
        }
        int voidId = v.palette().indexOf(STRUCTURE_VOID);
        int minY = level.getMinY(), maxY = level.getMaxY();
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean[] occupied = new boolean[v.width * v.length];
        v.forEachBlock((x, y, z, s) -> {
            if (v.idAt(x, y, z) != voidId) {
                occupied[z * v.width + x] = true;
            }
        });
        // Bottom-up, which also makes a partially placed building look sensible.
        for (int y = 0; y < v.height; y++) {
            for (int z = 0; z < v.length; z++) {
                for (int x = 0; x < v.width; x++) {
                    int id = v.idAt(x, y, z);
                    if (id == voidId || id == 0 && !occupied[z * v.width + x]) {
                        continue;
                    }
                    BlockState st = id == 0 ? air : states[id];
                    BlockPos p = base.offset(x - v.originX, y - v.originY, z - v.originZ);
                    if (st != null && p.getY() >= minY && p.getY() <= maxY) {
                        edits.add(new Edit(p, st));
                    }
                }
            }
        }
        QUEUE.add(new Job(level, edits, owner, false, onDone));
        return invalid;
    }

    /** Reverts the owner's most recent placement. Returns false if there is nothing to undo. */
    public static boolean undo(UUID owner, Consumer<Integer> onDone) {
        Deque<Job> h = HISTORY.get(owner);
        if (h == null || h.isEmpty()) {
            return false;
        }
        Job last = h.pop();
        List<Edit> restore = new ArrayList<>(last.previous);
        java.util.Collections.reverse(restore);
        QUEUE.add(new Job(last.level, restore, owner, true, onDone));
        return true;
    }

    public static boolean busy() {
        return !QUEUE.isEmpty();
    }

    /** Called at the end of every server tick. */
    public static void tick(MinecraftServer server) {
        int budget = BLOCKS_PER_TICK;
        while (budget > 0 && !QUEUE.isEmpty()) {
            Job job = QUEUE.peek();
            int end = Math.min(job.edits.size(), job.next + budget);
            for (int i = job.next; i < end; i++) {
                Edit e = job.edits.get(i);
                if (!job.isUndo) {
                    job.previous.add(new Edit(e.pos, job.level.getBlockState(e.pos)));
                }
                job.level.setBlock(e.pos, e.state, FLAGS);
            }
            budget -= end - job.next;
            job.next = end;
            if (job.next >= job.edits.size()) {
                QUEUE.poll();
                if (!job.isUndo) {
                    Deque<Job> h = HISTORY.computeIfAbsent(job.owner, k -> new ArrayDeque<>());
                    h.push(job);
                    while (h.size() > UNDO_DEPTH) {
                        h.removeLast();
                    }
                }
                if (job.onDone != null) {
                    job.onDone.accept(job.edits.size());
                }
            }
        }
    }

    /** Drops queued work and history (server stopping). */
    public static void clear() {
        QUEUE.clear();
        HISTORY.clear();
    }
}
