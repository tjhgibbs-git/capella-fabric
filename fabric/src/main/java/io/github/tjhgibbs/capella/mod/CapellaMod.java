package io.github.tjhgibbs.capella.mod;

import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.params.BuildingParams;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common (both sides) entrypoint: networking, placement queue, commands. */
public final class CapellaMod implements ModInitializer {
    public static final String MOD_ID = "capella";
    public static final Logger LOG = LoggerFactory.getLogger("Capella");
    /** Placing from the GUI must not reach further than this from the player. */
    static final double MAX_PLACE_DISTANCE = 256;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(PlaceBuildingPayload.TYPE, PlaceBuildingPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PlaceBuildingPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            // Receivers run on the server thread (checked in a 26.3 client, 2026-10-29).
            place(player, payload.params(), payload.pos(), payload.facing());
        });
        ServerTickEvents.END_SERVER_TICK.register(BuildingPlacer::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> BuildingPlacer.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CapellaCommands.register(dispatcher));
        LOG.info("Capella building generator ready");
    }

    /** Whether this player may build: creative mode (which covers singleplayer building). */
    static boolean mayPlace(ServerPlayer player) {
        // VERIFY(26.3): permission checks moved around in 1.21.x; creative is a stable proxy.
        return player.getAbilities().instabuild;
    }

    /**
     * Validates, generates off-thread, then queues placement with the west door's threshold at
     * {@code pos} and the nave running in {@code facing} (a {@code get2DDataValue}). Server thread.
     */
    static void place(ServerPlayer player, String encoded, BlockPos pos, int facing) {
        if (!mayPlace(player)) {
            player.sendSystemMessage(Component.translatable("capella.msg.need_creative"));
            return;
        }
        if (player.blockPosition().distSqr(pos) > MAX_PLACE_DISTANCE * MAX_PLACE_DISTANCE) {
            player.sendSystemMessage(Component.translatable("capella.msg.too_far"));
            return;
        }
        BuildingParams params;
        try {
            params = BuildingParams.decode(encoded);
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.translatable("capella.msg.bad_params", e.getMessage()));
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        var server = level.getServer();
        player.sendSystemMessage(Component.translatable("capella.msg.generating"));
        Generation.generate(params, BuildingGenerator.turnsFacing(facing)).whenComplete((building, error) -> server.execute(() -> {
            if (error != null) {
                LOG.error("Building generation failed for {}", encoded, error);
                player.sendSystemMessage(Component.translatable("capella.msg.failed", String.valueOf(error.getMessage())));
                return;
            }
            int invalid = BuildingPlacer.enqueue(level, building.volume(), pos, player.getUUID(),
                    count -> player.sendSystemMessage(Component.translatable("capella.msg.placed", count)));
            if (invalid > 0) {
                player.sendSystemMessage(Component.translatable("capella.msg.invalid_blocks", invalid));
            }
        }));
    }
}
