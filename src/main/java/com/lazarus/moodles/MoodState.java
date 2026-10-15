package com.lazarus.moodles;

import net.minecraft.nbt.CompoundTag;
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
    private final float[] values = new float[KEYS.length];
    private boolean wasThreatened;
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
        state.footDistanceCm = Math.max(0, Math.min(MAX_FOOT_DISTANCE_CM, tag.getLong("FootDistanceCm")));
        state.lastFootStatCm = tag.contains("LastFootStatCm")
                ? tag.getLong("LastFootStatCm") : footStats(player);
        return state;
    }

    void save(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < values.length; i++) tag.putFloat(KEYS[i], values[i]);
        tag.putBoolean("WasThreatened", wasThreatened);
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
        boolean relieved = forcedRelief >= 0 ? forcedRelief > 0 : values[1] >= 15;
        if (values[0] < target) values[0] = Math.min(target, values[0] + (critical ? 10 : unsafe ? 9 : 8));
        else values[0] = Math.max(target, values[0] - (relieved ? 9 : 5));
        long currentFootStatCm = footStats(player);
        if (currentFootStatCm > lastFootStatCm) {
            footDistanceCm = Math.min(MAX_FOOT_DISTANCE_CM,
                    footDistanceCm + currentFootStatCm - lastFootStatCm);
        }
        lastFootStatCm = currentFootStatCm;
        if (wasThreatened && !threatened) move(1, 35);
        else move(1, threatened ? -8 : -2);
        wasThreatened = threatened;
    }

    void onSleep(ServerPlayer player) {
        footDistanceCm = 0;
        lastFootStatCm = footStats(player);
    }

    void onHurt(float damage) {
        values[0] = Math.min(84, values[0] + Math.min(30, 10 + damage * 3));
    }

    byte[] levels(ServerPlayer player) {
        byte[] levels = new byte[COUNT];
        levels[0] = level(values[0]);
        levels[0] = (byte) Math.min(3, levels[0]);
        levels[1] = (byte) Math.min(5, footDistanceCm / CM_PER_LEVEL);
        levels[2] = level(values[1]);
        float health = player.getHealth() / player.getMaxHealth();
        levels[3] = (byte) (health >= 1 ? 0 : health <= 0.25f ? 3 : health <= 0.5f ? 2 : 1);
        for (int i = 0; i < COUNT; i++) {
            int forced = testLevel(player, i);
            if (forced >= 0) levels[i] = (byte) forced;
        }
        return levels;
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
