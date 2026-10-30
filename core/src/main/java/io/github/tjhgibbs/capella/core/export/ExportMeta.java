package io.github.tjhgibbs.capella.core.export;

/**
 * Descriptive data stored alongside the blocks.
 *
 * @param dataVersion the game's data version (SharedConstants in-game); schematic readers use it
 *                    to upgrade block states, so pass the real one when it is known
 * @param params      the encoded TreeParams, so a schematic can be regenerated or tweaked later
 */
public record ExportMeta(String name, String author, int dataVersion, String params, long createdMillis) {
    /** Placeholder for tools running outside the game. Readers treat it as "unknown/old". */
    public static final int UNKNOWN_DATA_VERSION = 0;
}
