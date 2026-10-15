package com.lazarus.moodles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;

final class MoodState {
    static final String TAG = "LazarusMoodles";
    static final int COUNT = 6;
    private static final String[] KEYS = {"Tense", "Fearful", "Panicked", "Irritated", "Exhausted", "Relieved"};
    private final float[] values = new float[COUNT];
    private boolean wasThreatened;

    static MoodState load(ServerPlayer player) {
        MoodState state = new MoodState();
        CompoundTag tag = player.getPersistentData().getCompound(TAG);
        for (int i = 0; i < COUNT; i++) state.values[i] = tag.getFloat(KEYS[i]);
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
        boolean unsafe = nearby >= 3 || health < 0.45f;

        move(0, threatened || light < 4 ? 8 : -5);
        move(1, unsafe ? 9 : threatened ? 3 : -5);
        move(2, nearby >= 6 || nearby >= 3 && health < 0.5f ? 10 : -7);
        move(3, -2);
        move(4, player.isSprinting() ? 4 : player.getFoodData().getFoodLevel() < 7 ? 2 : -3);
        if (wasThreatened && !threatened) move(5, 35);
        else move(5, threatened ? -8 : -2);
        wasThreatened = threatened;
    }

    void onHurt(float damage) {
        move(0, 12);
        move(1, 9);
        move(2, damage >= 5 ? 12 : 5);
        move(3, Math.min(30, 10 + damage * 3));
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
