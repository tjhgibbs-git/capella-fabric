package io.github.tjhgibbs.capella.core.export;

import io.github.tjhgibbs.capella.core.nbt.Nbt;
import io.github.tjhgibbs.capella.core.preview.BlockColors;
import io.github.tjhgibbs.capella.core.preview.PreviewRenderer;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Axiom's native blueprint ({@code .bp}), which shows up in Axiom's blueprint browser with a
 * thumbnail. Layout, as read and written by Axiom's {@code BlueprintIo} (checked against
 * Moulberry/AxiomPaperPlugin, 2026-10-31):
 *
 * <pre>
 *   int magic 0x0AE5BB36
 *   int length, header NBT (uncompressed): Version=2, Name, Author, Tags, ThumbnailYaw/Pitch,
 *                                          LockedThumbnail, BlockCount, ContainsAir
 *   int length, PNG thumbnail
 *   int length, gzipped NBT: DataVersion, BlockRegion[{X,Y,Z, BlockStates}], BlockEntities, Entities
 * </pre>
 *
 * {@code BlockStates} is a paletted container for one 16^3 section, keyed by section coordinates,
 * encoded with {@code BlockState.CODEC}. On 26.3 that codec reads a palette entry as a block id
 * string or {@code {id, properties}}; the older {@code {Name, Properties}} shape is rejected.
 * Version 2 blueprints mark empty cells with {@code minecraft:void_air}, so the church pastes
 * without clearing what is around it. Positions are relative to the west door threshold. Sunken interiors retain explicit cave_air.
 * {@code PlacementGameTests.axiomBlueprintDecodesWithVanillaCodec} decodes the output with the
 * game's own codec.
 */
public final class AxiomBlueprint implements SchematicFormat {
    static final int MAGIC = 0x0AE5BB36;
    static final int VERSION = 2;
    static final String EMPTY = "minecraft:void_air";
    static final int THUMBNAIL = 256;

    @Override
    public String id() {
        return "axiom";
    }

    @Override
    public String extension() {
        return ".bp";
    }

    @Override
    public void write(BlockVolume v, ExportMeta meta, OutputStream out) throws IOException {
        DataOutputStream d = new DataOutputStream(out);
        d.writeInt(MAGIC);
        lengthPrefixed(d, header(v, meta));
        lengthPrefixed(d, thumbnail(v));
        ByteArrayOutputStream blocks = new ByteArrayOutputStream();
        Nbt.writeGzip(blocks, "", blockData(v, meta));
        lengthPrefixed(d, blocks.toByteArray());
        d.flush();
    }

    private static void lengthPrefixed(DataOutputStream d, byte[] bytes) throws IOException {
        d.writeInt(bytes.length);
        d.write(bytes);
    }

    private static byte[] header(BlockVolume v, ExportMeta meta) throws IOException {
        Map<String, Object> h = Nbt.compound();
        h.put("Version", VERSION);
        h.put("Name", meta.name());
        h.put("Author", meta.author() == null ? "" : meta.author());
        h.put("Tags", Nbt.ListTag.of(Nbt.STRING, List.of("church", "Capella")));
        h.put("ThumbnailYaw", 135f);
        h.put("ThumbnailPitch", 30f);
        h.put("LockedThumbnail", (byte) 0);
        h.put("BlockCount", v.blockCount());
        h.put("ContainsAir", (byte) (v.palette().contains("minecraft:cave_air") ? 1 : 0));
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        Nbt.write(b, "", h);
        return b.toByteArray();
    }

    /** The church from three-quarters above, on a transparent background. */
    static byte[] thumbnail(BlockVolume v) throws IOException {
        PreviewRenderer.View view = new PreviewRenderer.View(THUMBNAIL, THUMBNAIL, Math.toRadians(35), Math.toRadians(25), 1, 0, 0, false);
        return Png.encode(PreviewRenderer.render(v, view, BlockColors::rgb), THUMBNAIL, THUMBNAIL);
    }

    private static Map<String, Object> blockData(BlockVolume v, ExportMeta meta) throws IOException {
        // Group blocks into 16^3 sections of base-relative coordinates, in a stable order.
        Map<Section, int[]> sections = new LinkedHashMap<>();
        v.forEachBlock((x, y, z, state) -> {
            int rx = x - v.originX, ry = y - v.originY, rz = z - v.originZ;
            int[] cells = sections.computeIfAbsent(new Section(rx >> 4, ry >> 4, rz >> 4), k -> new int[4096]); // 0 = empty, else volume palette id
            cells[((ry & 15) << 8) | ((rz & 15) << 4) | (rx & 15)] = v.idAt(x, y, z);
        });
        List<Object> regions = new ArrayList<>(sections.size());
        for (Map.Entry<Section, int[]> e : sections.entrySet()) {
            Map<String, Object> region = Nbt.compound();
            region.put("X", e.getKey().x());
            region.put("Y", e.getKey().y());
            region.put("Z", e.getKey().z());
            region.put("BlockStates", section(e.getValue(), v.palette()));
            regions.add(region);
        }
        Map<String, Object> root = Nbt.compound();
        if (meta.dataVersion() != ExportMeta.UNKNOWN_DATA_VERSION) {
            root.put("DataVersion", meta.dataVersion()); // absent = the reader's own version
        }
        root.put("BlockRegion", Nbt.ListTag.of(Nbt.COMPOUND, regions));
        root.put("BlockEntities", Nbt.ListTag.of(Nbt.COMPOUND, List.of()));
        root.put("Entities", Nbt.ListTag.of(Nbt.COMPOUND, List.of()));
        return root;
    }

    /** Section coordinates: block coordinates relative to the west door threshold, divided by 16 (floor). */
    private record Section(int x, int y, int z) {
    }

    /** One section in vanilla's paletted-container form: {palette:[{id,properties}], data:long[]}. */
    static Map<String, Object> section(int[] cells, List<String> volumePalette) throws IOException {
        Map<Integer, Integer> local = new LinkedHashMap<>();
        local.put(0, 0); // volume air -> void_air, never placed
        int[] ids = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            ids[i] = local.computeIfAbsent(cells[i], id -> local.size());
        }
        // Above 256 states vanilla switches to the global palette, whose packing depends on the
        // registry. A section normally never gets near that in one section; refuse rather than write garbage.
        if (local.size() > 256) {
            throw new IOException("too many block states in one section for a blueprint");
        }
        List<Object> palette = new ArrayList<>(local.size());
        for (int volumeId : local.keySet()) {
            palette.add(paletteEntry(volumeId == 0 ? EMPTY : volumePalette.get(volumeId)));
        }
        Map<String, Object> out = Nbt.compound();
        out.put("palette", Nbt.ListTag.of(Nbt.COMPOUND, palette));
        if (palette.size() > 1) {
            out.put("data", pack(ids, bitsFor(palette.size())));
        }
        return out;
    }

    /** Bits per entry for a section palette: at least 4, as vanilla's linear palette uses. */
    static int bitsFor(int paletteSize) {
        return Math.max(4, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
    }

    /** Vanilla SimpleBitStorage layout: entries never span two longs. */
    static long[] pack(int[] values, int bits) {
        int perLong = 64 / bits;
        long[] out = new long[(values.length + perLong - 1) / perLong];
        for (int i = 0; i < values.length; i++) {
            out[i / perLong] |= (long) values[i] << (i % perLong * bits);
        }
        return out;
    }

    /**
     * 26.3's {@code BlockState.FULL_CODEC} shape. Always a compound (the codec would also take a bare
     * id string for a default state, but NBT lists cannot mix strings and compounds).
     */
    private static Map<String, Object> paletteEntry(String state) {
        BlockStateString s = BlockStateString.parse(state);
        Map<String, Object> entry = Nbt.compound();
        entry.put("id", s.name());
        if (!s.properties().isEmpty()) {
            Map<String, Object> props = Nbt.compound();
            props.putAll(s.properties());
            entry.put("properties", props);
        }
        return entry;
    }
}
