package io.github.tjhgibbs.capella.mod;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges core's block-state strings to the game registry. All Minecraft API use for block
 * lookup is in this class, so a mapping change only touches one file.
 */
public final class McBlocks {
    private static final Map<String, BlockState> PARSED = new ConcurrentHashMap<>();
    private static final BlockState INVALID = null;

    private McBlocks() {
    }

    static Block block(String id) {
        int b = id.indexOf('[');
        Identifier key = Identifier.tryParse(b >= 0 ? id.substring(0, b) : id);
        if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getValue(key);
    }

    /** True if the id (with optional properties) names a real block state. */
    public static boolean isValid(String state) {
        return parse(state) != null;
    }

    /** Parses vanilla block-state syntax; null if unknown or malformed. Cached. */
    public static BlockState parse(String state) {
        BlockState cached = PARSED.get(state);
        if (cached != null) {
            return cached;
        }
        try {
            StringReader reader = new StringReader(state);
            BlockState s = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, reader, false).blockState();
            if (reader.canRead()) {
                return INVALID;
            }
            if (PARSED.size() < 10_000) {
                PARSED.put(state, s);
            }
            return s;
        } catch (CommandSyntaxException e) {
            return INVALID;
        }
    }

    /** The running game's data version, written into schematics. */
    public static int dataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }
}
