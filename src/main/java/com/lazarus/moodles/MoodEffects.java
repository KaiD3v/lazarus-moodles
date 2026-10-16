package com.lazarus.moodles;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

final class MoodEffects {
    private static final UUID ATTACK_SPEED = UUID.fromString("40a83fc2-e6cc-4d96-b6b7-8a969767125d");
    private static final UUID MOVEMENT_SPEED = UUID.fromString("e7c2ce7f-6f1f-4a7b-9f41-3db3bd3cc47a");
    private static final UUID STAMINA_REGEN = UUID.fromString("a4ad32ad-a3fa-4a90-a29d-b9661e61f3e6");
    private static final UUID SPRINT_COST = UUID.fromString("a1824426-bd7d-4f14-a409-a7761186d7f1");
    private static final UUID MAX_STAMINA = UUID.fromString("eddf6063-473d-417f-9210-7ec59130e67a");
    private static final UUID JUMP_COST = UUID.fromString("c1ac78cb-ea98-4a9a-8427-7951691db7d0");
    private static final UUID ATTACK_COST = UUID.fromString("6db04e2b-3ef6-4b65-9703-07caf2bfbff5");
    private static final Attribute PEAK_MAX_STAMINA = attribute("peakstamina", "max_stamina");
    private static final Attribute PEAK_JUMP_COST = attribute("peakstamina", "jump_cost_multiplier");
    private static final Attribute PEAK_ATTACK_COST = attribute("peakstamina", "attack_cost_multiplier");
    private static final float[] EXHAUSTED_FOOD_COST = {0, 0, 0, 0, 0.02f, 0.04f};
    private static final float[] EXHAUSTED_THIRST_COST = {0, 0, 0, 0, 0.012f, 0.024f};
    private static final Attribute PEAK_STAMINA_REGEN = attribute("peakstamina", "stamina_regen");
    private static final Attribute PEAK_SPRINT_COST = attribute("peakstamina", "sprint_cost_multiplier");
    private static final float[] FOOD_COST = {0, 0.02f, 0.05f, 0.10f};
    private static final float[] THIRST_COST = {0, 0.012f, 0.03f, 0.06f};
    private static final Method ADD_THIRST_EXHAUSTION = findThirstMethod();
    // Reloads take 1.10x/1.25x/1.45x as long: the reload clock advances this many ms less per 50 ms tick.
    private static final long[] RELOAD_LAG_MS = {0, 5, 10, 16};
    private static final Method TACZ_OPERATOR = method("com.tacz.guns.api.entity.IGunOperator",
            "fromLivingEntity", net.minecraft.world.entity.LivingEntity.class);
    private static final Method TACZ_DATA = method("com.tacz.guns.api.entity.IGunOperator", "getDataHolder");
    private static final Field TACZ_RELOAD_TIME = field("com.tacz.guns.entity.shooter.ShooterDataHolder", "reloadTimestamp");
    private static final Field TACZ_RELOAD_STATE = field("com.tacz.guns.entity.shooter.ShooterDataHolder", "reloadStateType");
    private static final Method TACZ_IS_RELOADING = method("com.tacz.guns.api.entity.ReloadState$StateType", "isReloading");
    private static final Map<ServerPlayer, Integer> FEAR = new WeakHashMap<>();

    private MoodEffects() {}

    static void apply(ServerPlayer player, byte[] levels) {
        applyModifiers(player, levels);
        int fear = levels[0];
        FEAR.put(player, fear);
        if (!player.isCreative() && !player.isSpectator()) {
            float food = FOOD_COST[fear] + EXHAUSTED_FOOD_COST[levels[1]];
            float thirst = THIRST_COST[fear] + EXHAUSTED_THIRST_COST[levels[1]];
            if (food > 0) player.getFoodData().addExhaustion(food);
            if (thirst > 0 && ADD_THIRST_EXHAUSTION != null) {
                try {
                    ADD_THIRST_EXHAUSTION.invoke(null, player, thirst);
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
                "Lazarus stamina recovery", playing ? -0.05 * exhaustion - 0.05 * pain + 0.05 * levels[2] - 0.10 * levels[6] - 0.05 * levels[7] : 0);
        modifier(player, PEAK_SPRINT_COST, SPRINT_COST,
                "Lazarus sprint cost", playing ? 0.06 * exhaustion : 0);
        modifier(player, PEAK_MAX_STAMINA, MAX_STAMINA, "Lazarus exhaustion max stamina",
                playing && exhaustion >= 4 ? (exhaustion == 5 ? -0.20 : -0.10) : 0);
        double heavyCost = playing && exhaustion >= 3 ? 0.10 * (exhaustion - 2) : 0;
        modifier(player, PEAK_JUMP_COST, JUMP_COST, "Lazarus exhaustion jump cost", heavyCost);
        modifier(player, PEAK_ATTACK_COST, ATTACK_COST, "Lazarus exhaustion attack cost", heavyCost);

    }

    static int fear(ServerPlayer player) {
        return FEAR.getOrDefault(player, 0);
    }

    // Called every server tick: pushes TaCZ's reload start time forward so the reload finishes later.
    static void slowReload(ServerPlayer player) {
        int fear = FEAR.getOrDefault(player, 0);
        if (fear == 0 || player.isCreative() || player.isSpectator() || TACZ_RELOAD_TIME == null
                || TACZ_OPERATOR == null || TACZ_DATA == null || TACZ_RELOAD_STATE == null || TACZ_IS_RELOADING == null) return;
        try {
            Object data = TACZ_DATA.invoke(TACZ_OPERATOR.invoke(null, player));
            long started = TACZ_RELOAD_TIME.getLong(data);
            Object state = TACZ_RELOAD_STATE.get(data);
            if (started > 0 && state != null && Boolean.TRUE.equals(TACZ_IS_RELOADING.invoke(state))) {
                double steadiness = Math.min(1, Courage.value(player, Courage.FEAR_STEADINESS));
                TACZ_RELOAD_TIME.setLong(data, started + Math.round(RELOAD_LAG_MS[fear] * (1 - steadiness)));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // If TaCZ changes its internals, reloads simply keep their normal speed.
        }
    }

    private static Method method(String className, String name, Class<?>... arguments) {
        try {
            return Class.forName(className).getMethod(name, arguments);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Field field(String className, String name) {
        try {
            return Class.forName(className).getField(name);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
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
