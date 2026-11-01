package io.github.tjhgibbs.capella.core.export;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Minimal PNG encoder (8-bit RGBA, no filtering). Avoids ImageIO, which would load AWT inside
 * the game; on macOS that can stall the client.
 */
public final class Png {
    private Png() {
    }

    /** Encodes row-major ARGB pixels. */
    public static byte[] encode(int[] argb, int width, int height) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(raw)) {
            byte[] row = new byte[1 + width * 4]; // filter byte 0 (none), then RGBA
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int c = argb[y * width + x];
                    int o = 1 + x * 4;
                    row[o] = (byte) (c >>> 16);
                    row[o + 1] = (byte) (c >>> 8);
                    row[o + 2] = (byte) c;
                    row[o + 3] = (byte) (c >>> 24);
                }
                z.write(row);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(out);
        d.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(ihdr);
        h.writeInt(width);
        h.writeInt(height);
        h.write(new byte[]{8, 6, 0, 0, 0}); // 8 bits, RGBA, deflate, adaptive filtering, no interlace
        chunk(d, "IHDR", ihdr.toByteArray());
        chunk(d, "IDAT", raw.toByteArray());
        chunk(d, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(DataOutputStream d, String type, byte[] data) throws IOException {
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        d.writeInt(data.length);
        d.write(t);
        d.write(data);
        d.writeInt((int) crc.getValue());
    }
}
