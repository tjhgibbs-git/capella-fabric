package io.github.tjhgibbs.capella.core.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Minimal Java-edition NBT: enough to write schematics and structures without Minecraft on the
 * classpath, and to read them back in tests. Values map to plain Java types:
 * Byte, Short, Integer, Long, Float, Double, byte[], String, {@link ListTag}, Map (compound),
 * int[], long[].
 */
public final class Nbt {
    public static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6,
            BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    private Nbt() {
    }

    /** A list with an explicit element type (needed for empty lists). */
    public record ListTag(byte elementType, List<Object> values) {
        public static ListTag of(byte type, List<?> values) {
            return new ListTag(type, new ArrayList<>(values));
        }
    }

    public static Map<String, Object> compound() {
        return new LinkedHashMap<>();
    }

    /** Writes a gzip-compressed file with an unnamed (or named) root compound. */
    public static void writeGzip(OutputStream out, String rootName, Map<String, Object> root) throws IOException {
        GZIPOutputStream gz = new GZIPOutputStream(out);
        DataOutputStream d = new DataOutputStream(gz);
        d.writeByte(COMPOUND);
        d.writeUTF(rootName);
        writeCompoundBody(d, root);
        d.flush();
        gz.finish();
    }

    public record Root(String name, Map<String, Object> value) {
    }

    public static Root readGzip(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new GZIPInputStream(in));
        byte type = d.readByte();
        if (type != COMPOUND) {
            throw new IOException("root is not a compound: " + type);
        }
        String name = d.readUTF();
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) readPayload(d, COMPOUND, 0);
        return new Root(name, value);
    }

    static byte typeOf(Object v) {
        if (v instanceof Byte) return BYTE;
        if (v instanceof Short) return SHORT;
        if (v instanceof Integer) return INT;
        if (v instanceof Long) return LONG;
        if (v instanceof Float) return FLOAT;
        if (v instanceof Double) return DOUBLE;
        if (v instanceof byte[]) return BYTE_ARRAY;
        if (v instanceof String) return STRING;
        if (v instanceof ListTag) return LIST;
        if (v instanceof Map) return COMPOUND;
        if (v instanceof int[]) return INT_ARRAY;
        if (v instanceof long[]) return LONG_ARRAY;
        throw new IllegalArgumentException("not an NBT value: " + (v == null ? "null" : v.getClass()));
    }

    @SuppressWarnings("unchecked")
    private static void writeCompoundBody(DataOutputStream d, Map<String, Object> map) throws IOException {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            byte t = typeOf(e.getValue());
            d.writeByte(t);
            d.writeUTF(e.getKey());
            writePayload(d, t, e.getValue());
        }
        d.writeByte(END);
    }

    @SuppressWarnings("unchecked")
    private static void writePayload(DataOutputStream d, byte t, Object v) throws IOException {
        switch (t) {
            case BYTE -> d.writeByte((Byte) v);
            case SHORT -> d.writeShort((Short) v);
            case INT -> d.writeInt((Integer) v);
            case LONG -> d.writeLong((Long) v);
            case FLOAT -> d.writeFloat((Float) v);
            case DOUBLE -> d.writeDouble((Double) v);
            case BYTE_ARRAY -> {
                byte[] a = (byte[]) v;
                d.writeInt(a.length);
                d.write(a);
            }
            case STRING -> d.writeUTF((String) v);
            case LIST -> {
                ListTag l = (ListTag) v;
                d.writeByte(l.values().isEmpty() ? END : l.elementType());
                d.writeInt(l.values().size());
                for (Object o : l.values()) {
                    if (typeOf(o) != l.elementType()) {
                        throw new IllegalArgumentException("mixed list element " + o);
                    }
                    writePayload(d, l.elementType(), o);
                }
            }
            case COMPOUND -> writeCompoundBody(d, (Map<String, Object>) v);
            case INT_ARRAY -> {
                int[] a = (int[]) v;
                d.writeInt(a.length);
                for (int i : a) {
                    d.writeInt(i);
                }
            }
            case LONG_ARRAY -> {
                long[] a = (long[]) v;
                d.writeInt(a.length);
                for (long l : a) {
                    d.writeLong(l);
                }
            }
            default -> throw new IllegalArgumentException("type " + t);
        }
    }

    private static Object readPayload(DataInputStream d, byte t, int depth) throws IOException {
        if (depth > 512) {
            throw new IOException("NBT nested too deeply");
        }
        switch (t) {
            case BYTE: return d.readByte();
            case SHORT: return d.readShort();
            case INT: return d.readInt();
            case LONG: return d.readLong();
            case FLOAT: return d.readFloat();
            case DOUBLE: return d.readDouble();
            case BYTE_ARRAY: {
                byte[] a = new byte[checkedLength(d.readInt())];
                d.readFully(a);
                return a;
            }
            case STRING: return d.readUTF();
            case LIST: {
                byte et = d.readByte();
                int n = checkedLength(d.readInt());
                List<Object> out = new ArrayList<>(Math.min(n, 1 << 16));
                for (int i = 0; i < n; i++) {
                    out.add(readPayload(d, et, depth + 1));
                }
                return new ListTag(et, out);
            }
            case COMPOUND: {
                Map<String, Object> m = new LinkedHashMap<>();
                while (true) {
                    byte ct = d.readByte();
                    if (ct == END) {
                        return m;
                    }
                    String name = d.readUTF();
                    m.put(name, readPayload(d, ct, depth + 1));
                }
            }
            case INT_ARRAY: {
                int[] a = new int[checkedLength(d.readInt())];
                for (int i = 0; i < a.length; i++) {
                    a[i] = d.readInt();
                }
                return a;
            }
            case LONG_ARRAY: {
                long[] a = new long[checkedLength(d.readInt())];
                for (int i = 0; i < a.length; i++) {
                    a[i] = d.readLong();
                }
                return a;
            }
            default:
                throw new IOException("bad tag type " + t);
        }
    }

    private static int checkedLength(int n) throws IOException {
        if (n < 0 || n > 64 * 1024 * 1024) {
            throw new IOException("bad length " + n);
        }
        return n;
    }
}
