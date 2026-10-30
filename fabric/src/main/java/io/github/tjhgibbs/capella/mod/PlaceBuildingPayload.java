package io.github.tjhgibbs.capella.mod;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: "build this here, facing this way". Only the encoded params travel;
 * generation is deterministic, so the server rebuilds exactly the building the client previewed.
 * {@code facing} is the player's horizontal direction ({@code Direction.get2DDataValue()}); the
 * nave runs away from the player.
 */
public record PlaceBuildingPayload(String params, BlockPos pos, int facing) implements CustomPacketPayload {
    public static final Type<PlaceBuildingPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CapellaMod.MOD_ID, "place_building"));
    /** Generous but bounded: a full encoding is well under 2 KB. */
    static final int MAX_PARAMS = 8192;
    public static final StreamCodec<ByteBuf, PlaceBuildingPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_PARAMS), PlaceBuildingPayload::params,
            BlockPos.STREAM_CODEC, PlaceBuildingPayload::pos,
            ByteBufCodecs.VAR_INT, PlaceBuildingPayload::facing,
            PlaceBuildingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
