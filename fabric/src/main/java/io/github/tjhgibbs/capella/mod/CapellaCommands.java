package io.github.tjhgibbs.capella.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.tjhgibbs.capella.core.export.ExportMeta;
import io.github.tjhgibbs.capella.core.export.Exporter;
import io.github.tjhgibbs.capella.core.export.SchematicFormat;
import io.github.tjhgibbs.capella.core.params.Presets;
import io.github.tjhgibbs.capella.core.params.BuildingParams;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code /capella place <preset> [seed]}, {@code /capella build <encoded params>},
 * {@code /capella undo}, {@code /capella export <preset> <format> [seed]},
 * {@code /capella presets}. The GUI covers all of this; commands are for scripting and tests.
 * Placing builds at the player's feet with the nave running the way the player faces.
 */
final class CapellaCommands {
    private CapellaCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("capella")
                .then(Commands.literal("presets").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(String.join(", ",
                            Presets.all().stream().map(Presets.Preset::id).toList())), false);
                    return 1;
                }))
                .then(Commands.literal("place")
                        .then(presetArg()
                                .executes(ctx -> place(ctx, randomSeed()))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(ctx -> place(ctx, LongArgumentType.getLong(ctx, "seed"))))))
                .then(Commands.literal("build")
                        .then(Commands.argument("params", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            CapellaMod.place(p, StringArgumentType.getString(ctx, "params"), p.blockPosition(), p.getDirection().get2DDataValue());
                            return 1;
                        })))
                .then(Commands.literal("undo").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (!BuildingPlacer.undo(p.getUUID(), n -> p.sendSystemMessage(Component.translatable("capella.msg.undone", n)))) {
                        ctx.getSource().sendFailure(Component.translatable("capella.msg.nothing_to_undo"));
                        return 0;
                    }
                    return 1;
                }))
                .then(Commands.literal("export")
                        .then(presetArg()
                                .then(Commands.argument("format", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(SchematicFormat.all()).map(SchematicFormat::id), b))
                                        .executes(ctx -> export(ctx, randomSeed()))
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                .executes(ctx -> export(ctx, LongArgumentType.getLong(ctx, "seed"))))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> presetArg() {
        return Commands.argument("preset", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Presets.all().stream().map(Presets.Preset::id), b));
    }

    private static long randomSeed() {
        return ThreadLocalRandom.current().nextLong(1, 1L << 48);
    }

    private static BuildingParams params(CommandContext<CommandSourceStack> ctx, long seed) {
        String id = StringArgumentType.getString(ctx, "preset");
        return Presets.exists(id) ? Presets.get(id).params(seed) : null;
    }

    private static int place(CommandContext<CommandSourceStack> ctx, long seed) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BuildingParams p = params(ctx, seed);
        if (p == null) {
            ctx.getSource().sendFailure(Component.translatable("capella.msg.unknown_preset"));
            return 0;
        }
        CapellaMod.place(player, p.encode(), player.blockPosition(), player.getDirection().get2DDataValue());
        return 1;
    }

    private static int export(CommandContext<CommandSourceStack> ctx, long seed) {
        BuildingParams p = params(ctx, seed);
        SchematicFormat format = SchematicFormat.byId(StringArgumentType.getString(ctx, "format"));
        if (p == null || format == null) {
            ctx.getSource().sendFailure(Component.translatable(p == null ? "capella.msg.unknown_preset" : "capella.msg.unknown_format"));
            return 0;
        }
        CommandSourceStack src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "preset") + "_" + p.seed();
        int dataVersion = McBlocks.dataVersion();
        Generation.generate(p).whenComplete((building, error) -> {
            try {
                if (error != null) {
                    throw error;
                }
                Path out = Exporter.write(format, building.volume(),
                        new ExportMeta(name, src.getTextName(), dataVersion, p.encode(), System.currentTimeMillis()), ExportPaths.dirFor(format));
                src.getServer().execute(() -> src.sendSuccess(() -> Component.translatable("capella.msg.exported", out.toString()), false));
            } catch (Throwable t) {
                CapellaMod.LOG.error("Export failed", t);
                src.getServer().execute(() -> src.sendFailure(Component.translatable("capella.msg.failed", String.valueOf(t.getMessage()))));
            }
        });
        return 1;
    }
}
