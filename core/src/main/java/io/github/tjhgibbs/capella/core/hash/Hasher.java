package io.github.tjhgibbs.capella.core.hash;

/** Incremental hash, for content and occupancy digests. */
public final class Hasher {
    private int h = Hash.FNV_OFFSET;

    public Hasher u32(int v) {
        h = Hash.mixUint32(h, v);
        return this;
    }

    public Hasher num(double v) {
        h = Hash.mixNumber(h, v);
        return this;
    }

    public Hasher str(String s) {
        h = Hash.mixString(h, s);
        return this;
    }

    public Hasher bytes(byte[] b) {
        for (byte x : b) h = Hash.mixByte(h, x);
        return this;
    }

    public long digest() {
        return Hash.avalanche(h) & 0xffffffffL;
    }

    public String hex() {
        return Hash.toHex(digest());
    }
}
