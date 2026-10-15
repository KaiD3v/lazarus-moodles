package com.lazarus.moodles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.stats.Stats;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

final class MoodState {
    static final String TAG = "LazarusMoodles";
    static final int COUNT = 4;
    private static final String[] KEYS = {"Stress", "Relieved"};
    private static final long CM_PER_LEVEL = 100_000;
    private static final long MAX_FOOT_DISTANCE_CM = 5 * CM_PER_LEVEL;
    private static final Map<ServerPlayer, byte[]> TEST_LEVELS = new WeakHashMap<>();
    // Game time until which a recent hit keeps Pain one level higher.
    private static final Map<ServerPlayer, Long> ACUTE_PAIN_UNTIL = new WeakHashMap<>();
    private static final int ACUTE_PAIN_TICKS = 100;
    // Seconds spent in bed (full Exhausted reset at 2 minutes) and resting crouched and still (-1 level per minute).
    private static final Map<ServerPlayer, Integer> BED_SECONDS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> REST_SECONDS = new WeakHashMap<>();
    private static final int BED_RESET_SECONDS = 120;
    private static final int REST_LEVEL_SECONDS = 60;
    private static final int REST_MIN_LEVEL = 2;
    private static final ResourceLocation[] PAINKILLERS = {
            new ResourceLocation("legendarysurvivaloverhaul", "painkiller"),
            new ResourceLocation("apocalypsenow", "painkillerseffect")};
    // Relief gained when escaping from Tense/Fearful/Panicked; escaping without fear gives none.
    private static final float[] RELIEF_GAIN = {0, 20, 40, 70};
    // Fear recovery per second at Relieved 0..4.
    private static final float[] FEAR_RECOVERY = {5, 7, 8, 9, 10};
    private final float[] values = new float[KEYS.length];
    private boolean wasThreatened;
    // Highest fear reached while it was rising during the current threat; cleared when relief is granted.
    private float peakFear;
    private long footDistanceCm;
    private long lastFootStatCm;

    static MoodState load(ServerPlayer player) {
        MoodState state = new MoodState();
        CompoundTag tag = player.getPersistentData().getCompound(TAG);
        for (int i = 0; i < state.values.length; i++) state.values[i] = tag.getFloat(KEYS[i]);
        if (!tag.contains("Stress")) {
            if (tag.getFloat("Irritated") >= 15) state.values[0] = 60;
            else if (tag.getFloat("Panicked") >= 15) state.values[0] = 60;
            else if (tag.getFloat("Fearful") >= 15) state.values[0] = 35;
            else if (tag.getFloat("Tense") >= 15) state.values[0] = 15;
        }
        state.values[0] = Math.min(84, state.values[0]);
        state.wasThreatened = tag.getBoolean("WasThreatened");
        state.peakFear = tag.getFloat("PeakFear");
        state.footDistanceCm = Math.max(0, Math.min(MAX_FOOT_DISTANCE_CM, tag.getLong("FootDistanceCm")));
        state.lastFootStatCm = tag.contains("LastFootStatCm")
                ? tag.getLong("LastFootStatCm") : footStats(player);
        return state;
    }

    void save(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < values.length; i++) tag.putFloat(KEYS[i], values[i]);
        tag.putBoolean("WasThreatened", wasThreatened);
        tag.putFloat("PeakFear", peakFear);
        tag.putLong("FootDistanceCm", footDistanceCm);
        tag.putLong("LastFootStatCm", lastFootStatCm);
        player.getPersistentData().put(TAG, tag);
    }

    void update(ServerPlayer player) {
        int nearby = player.serverLevel().getEntitiesOfClass(Monster.class,
                player.getBoundingBox().inflate(18), Monster::isAlive).size();
        int light = player.serverLevel().getMaxLocalRawBrightness(player.blockPosition());
        float health = player.getHealth() / player.getMaxHealth();
        boolean threatened = nearby > 0;
        boolean critical = nearby >= 6 || nearby >= 3 && health < 0.5f;
        boolean unsafe = nearby >= 3 || health < 0.45f;
        float target = critical ? 84 : unsafe ? 59 : threatened || light < 4 ? 34 : 0;
        int forcedRelief = testLevel(player, 2);
        int relief = Math.min(4, forcedRelief >= 0 ? forcedRelief : level(values[1]));
        float fearBefore = values[0];
        if (values[0] < target) values[0] = Math.min(target, values[0] + (critical ? 10 : unsafe ? 9 : 8));
        else values[0] = Math.max(target, values[0] - FEAR_RECOVERY[relief]);
        // Only fear that is rising counts, so a monster wandering in and out of range while fear decays gives no new relief.
        if (threatened && values[0] > fearBefore) peakFear = Math.max(peakFear, values[0]);
        long currentFootStatCm = footStats(player);
        boolean still = currentFootStatCm == lastFootStatCm;
        if (currentFootStatCm > lastFootStatCm) {
            footDistanceCm = Math.min(MAX_FOOT_DISTANCE_CM,
                    footDistanceCm + currentFootStatCm - lastFootStatCm);
        }
        lastFootStatCm = currentFootStatCm;
        rest(player, still);
        if (wasThreatened && !threatened) {
            move(1, RELIEF_GAIN[Math.min(3, level(peakFear))]);
            peakFear = 0;
        } else {
            move(1, threatened ? -8 : -2);
        }
        wasThreatened = threatened;
    }

    // On servers the night rarely gets skipped, so two minutes in bed count as a full sleep.
    private void rest(ServerPlayer player, boolean still) {
        if (player.isSleeping()) {
            if (BED_SECONDS.merge(player, 1, Integer::sum) == BED_RESET_SECONDS) onSleep(player);
        } else {
            BED_SECONDS.remove(player);
        }
        boolean resting = still && player.isCrouching() && player.onGround() && values[0] < 15;
        if (!resting) {
            REST_SECONDS.remove(player);
            return;
        }
        long level = footDistanceCm / CM_PER_LEVEL;
        if (REST_SECONDS.merge(player, 1, Integer::sum) % REST_LEVEL_SECONDS == 0 && level > REST_MIN_LEVEL) {
            footDistanceCm = (level - 1) * CM_PER_LEVEL;
        }
    }

    void onSleep(ServerPlayer player) {
        footDistanceCm = 0;
        lastFootStatCm = footStats(player);
    }

    void onHurt(float damage) {
        values[0] = Math.min(84, values[0] + Math.min(30, 10 + damage * 3));
        if (wasThreatened) peakFear = Math.max(peakFear, values[0]);
    }

    byte[] levels(ServerPlayer player) {
        byte[] levels = new byte[COUNT];
        levels[0] = level(values[0]);
        levels[0] = (byte) Math.min(3, levels[0]);
        levels[1] = (byte) Math.min(5, footDistanceCm / CM_PER_LEVEL);
        levels[2] = level(values[1]);
        float health = player.getHealth() / player.getMaxHealth();
        int pain = health > 0.8f ? 0 : health <= 0.25f ? 3 : health <= 0.5f ? 2 : 1;
        if (ACUTE_PAIN_UNTIL.getOrDefault(player, 0L) > player.level().getGameTime()) pain = Math.min(3, pain + 1);
        if (onPainkillers(player)) pain = Math.max(0, pain - 2);
        levels[3] = (byte) pain;
        for (int i = 0; i < COUNT; i++) {
            int forced = testLevel(player, i);
            if (forced >= 0) levels[i] = (byte) forced;
        }
        return levels;
    }

    static void markAcutePain(ServerPlayer player) {
        ACUTE_PAIN_UNTIL.put(player, player.level().getGameTime() + ACUTE_PAIN_TICKS);
    }

    static boolean onPainkillers(Player player) {
        for (ResourceLocation id : PAINKILLERS) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
            if (effect != null && player.hasEffect(effect)) return true;
        }
        return false;
    }

    static void setTestLevel(ServerPlayer player, int index, int level) {
        byte[] forced = TEST_LEVELS.computeIfAbsent(player, ignored -> {
            byte[] empty = new byte[COUNT];
            Arrays.fill(empty, (byte) -1);
            return empty;
        });
        forced[index] = (byte) level;
    }

    static void clearTestLevel(ServerPlayer player, int index) {
        byte[] forced = TEST_LEVELS.get(player);
        if (forced == null) return;
        forced[index] = -1;
        boolean empty = true;
        for (byte level : forced) if (level >= 0) empty = false;
        if (empty) TEST_LEVELS.remove(player);
    }

    static void clearTestLevels(ServerPlayer player) {
        TEST_LEVELS.remove(player);
    }

    static int testLevel(ServerPlayer player, int index) {
        byte[] forced = TEST_LEVELS.get(player);
        return forced == null ? -1 : forced[index];
    }

    private void move(int index, float delta) {
        values[index] = Math.max(0, Math.min(100, values[index] + delta));
    }

    private static byte level(float value) {
        return (byte) (value < 15 ? 0 : value < 35 ? 1 : value < 60 ? 2 : value < 85 ? 3 : 4);
    }

    private static long footStats(ServerPlayer player) {
        return (long) player.getStats().getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM))
                + player.getStats().getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))
                + player.getStats().getValue(Stats.CUSTOM.get(Stats.CROUCH_ONE_CM));
    }
}
