package com.lazarus.moodles;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class MoodleCommands {
    private static final String[] NAMES = {"fear", "exhausted", "relieved", "pain", "sedated", "withdrawal"};

    private MoodleCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("lazarusmoodles")
                .requires(source -> source.hasPermission(2) && source.getEntity() instanceof ServerPlayer)
                .then(category("fear", 0, 3))
                .then(category("exhausted", 1, 5))
                .then(category("relieved", 2, 4))
                .then(category("pain", 3, 3))
                .then(category("sedated", 4, 1))
                .then(category("withdrawal", 5, 1))
                .then(Commands.literal("status").executes(MoodleCommands::status))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context, -1))
                        .then(Commands.literal("fear").executes(context -> clear(context, 0)))
                        .then(Commands.literal("exhausted").executes(context -> clear(context, 1)))
                        .then(Commands.literal("relieved").executes(context -> clear(context, 2)))
                        .then(Commands.literal("pain").executes(context -> clear(context, 3)))
                        .then(Commands.literal("sedated").executes(context -> clear(context, 4)))
                        .then(Commands.literal("withdrawal").executes(context -> clear(context, 5))));
        dispatcher.register(Commands.literal("lm")
                .requires(source -> source.hasPermission(2) && source.getEntity() instanceof ServerPlayer)
                .redirect(dispatcher.register(root)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> category(String name, int index, int max) {
        return Commands.literal(name).then(Commands.argument("level", IntegerArgumentType.integer(0, max))
                .executes(context -> set(context, index)));
    }

    private static int set(CommandContext<CommandSourceStack> context, int index) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int level = IntegerArgumentType.getInteger(context, "level");
        MoodState.setTestLevel(player, index, level);
        refresh(player);
        context.getSource().sendSuccess(() -> Component.literal(
                "Test " + NAMES[index] + " set to " + level + ". Use /lm clear to return to natural moodles."), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context, int index) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (index < 0) MoodState.clearTestLevels(player);
        else MoodState.clearTestLevel(player, index);
        refresh(player);
        context.getSource().sendSuccess(() -> Component.literal(
                index < 0 ? "All test moodles cleared." : "Test " + NAMES[index] + " cleared."), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        byte[] levels = MoodState.load(player).levels(player);
        StringBuilder text = new StringBuilder("Moodles: ");
        for (int i = 0; i < levels.length; i++) {
            if (i > 0) text.append(", ");
            text.append(NAMES[i]).append('=').append(levels[i]);
            if (MoodState.testLevel(player, i) >= 0) text.append(" (test)");
        }
        int[] threats = MoodState.lastThreats(player);
        text.append(" | Threats: seen=").append(threats[0]).append(", chasing=").append(threats[1])
                .append(", close=").append(threats[2]).append(", remembered=").append(threats[3]);
        context.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static void refresh(ServerPlayer player) {
        MoodState state = MoodState.load(player);
        MoodEffects.applyModifiers(player, state.levels(player));
        LazarusMoodles.sync(player, state);
    }
}
