package com.lazarus.moodles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;

final class MoodState {
    static final String TAG = "LazarusMoodles";
    static final int COUNT = 3;
    private static final String[] KEYS = {"Stress", "Exhausted", "Relieved"};
    private final float[] values = new float[COUNT];
    private boolean wasThreatened;

    static MoodState load(ServerPlayer player) {
        MoodState state = new MoodState();
        CompoundTag tag = player.getPersistentData().getCompound(TAG);
        for (int i = 0; i < COUNT; i++) state.values[i] = tag.getFloat(KEYS[i]);
        if (!tag.contains("Stress")) {
            if (tag.getFloat("Irritated") >= 15) state.values[0] = 85;
            else if (tag.getFloat("Panicked") >= 15) state.values[0] = 60;
            else if (tag.getFloat("Fearful") >= 15) state.values[0] = 35;
            else if (tag.getFloat("Tense") >= 15) state.values[0] = 15;
        }
        state.wasThreatened = tag.getBoolean("WasThreatened");
        return state;
    }

    void save(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < COUNT; i++) tag.putFloat(KEYS[i], values[i]);
        tag.putBoolean("WasThreatened", wasThreatened);
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
        if (values[0] < target) values[0] = Math.min(target, values[0] + (critical ? 10 : unsafe ? 9 : 8));
        else values[0] = Math.max(target, values[0] - 5);
        move(1, player.isSprinting() ? 4 : player.getFoodData().getFoodLevel() < 7 ? 2 : -3);
        if (wasThreatened && !threatened) move(2, 35);
        else move(2, threatened ? -8 : -2);
        wasThreatened = threatened;
    }

    void onHurt(float damage) {
        move(0, Math.min(30, 10 + damage * 3));
    }

    byte[] levels() {
        byte[] levels = new byte[COUNT];
        for (int i = 0; i < COUNT; i++) {
            float value = values[i];
            levels[i] = (byte) (value < 15 ? 0 : value < 35 ? 1 : value < 60 ? 2 : value < 85 ? 3 : 4);
        }
        return levels;
    }

    private void move(int index, float delta) {
        values[index] = Math.max(0, Math.min(100, values[index] + delta));
    }
}
