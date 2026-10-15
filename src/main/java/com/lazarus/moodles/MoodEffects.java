package com.lazarus.moodles;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.UUID;

final class MoodEffects {
    private static final UUID ATTACK_SPEED = UUID.fromString("40a83fc2-e6cc-4d96-b6b7-8a969767125d");
    private static final UUID MOVEMENT_SPEED = UUID.fromString("e7c2ce7f-6f1f-4a7b-9f41-3db3bd3cc47a");
    private static final UUID STAMINA_REGEN = UUID.fromString("a4ad32ad-a3fa-4a90-a29d-b9661e61f3e6");
    private static final UUID SPRINT_COST = UUID.fromString("a1824426-bd7d-4f14-a409-a7761186d7f1");
    private static final Attribute PEAK_STAMINA_REGEN = attribute("peakstamina", "stamina_regen");
    private static final Attribute PEAK_SPRINT_COST = attribute("peakstamina", "sprint_cost_multiplier");
    private static final float[] FOOD_COST = {0, 0.02f, 0.05f, 0.10f};
    private static final float[] THIRST_COST = {0, 0.012f, 0.03f, 0.06f};
    private static final Method ADD_THIRST_EXHAUSTION = findThirstMethod();

    private MoodEffects() {}

    static void apply(ServerPlayer player, byte[] levels) {
        applyModifiers(player, levels);
        int fear = levels[0];
        if (!player.isCreative() && !player.isSpectator() && fear > 0) {
            player.getFoodData().addExhaustion(FOOD_COST[fear]);
            if (ADD_THIRST_EXHAUSTION != null) {
                try {
                    ADD_THIRST_EXHAUSTION.invoke(null, player, THIRST_COST[fear]);
                } catch (ReflectiveOperationException ignored) {
                    // An optional thirst integration must not interrupt player ticks.
                }
            }
        }
    }

    static void applyModifiers(ServerPlayer player, byte[] levels) {
        int exhaustion = levels[1];
        int pain = levels[3];
        boolean playing = !player.isCreative() && !player.isSpectator();

        modifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED, "Lazarus pain attack speed",
                playing ? -0.04 * pain : 0);
        modifier(player, Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED, "Lazarus exhaustion movement speed",
                playing && exhaustion >= 4 ? (exhaustion == 5 ? -0.06 : -0.03) : 0);
        modifier(player, PEAK_STAMINA_REGEN, STAMINA_REGEN,
                "Lazarus stamina recovery", playing ? -0.05 * exhaustion - 0.05 * pain : 0);
        modifier(player, PEAK_SPRINT_COST, SPRINT_COST,
                "Lazarus sprint cost", playing ? 0.06 * exhaustion : 0);

    }

    private static Attribute attribute(String namespace, String path) {
        return ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(namespace, path));
    }

    private static void modifier(ServerPlayer player, Attribute attribute, UUID id, String name, double amount) {
        if (attribute == null) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier previous = instance.getModifier(id);
        if (previous != null && Math.abs(previous.getAmount() - amount) < 0.0001) return;
        if (previous != null) instance.removeModifier(id);
        if (amount != 0) instance.addTransientModifier(
                new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static Method findThirstMethod() {
        try {
            return Class.forName("sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil")
                    .getMethod("addExhaustion", net.minecraft.world.entity.player.Player.class, float.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
