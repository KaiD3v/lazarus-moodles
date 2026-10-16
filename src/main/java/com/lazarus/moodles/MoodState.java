package com.lazarus.moodles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.stats.Stats;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

final class MoodState {
    static final String TAG = "LazarusMoodles";
    static final int COUNT = 8;
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
    // Threat detection: monsters only count within 18 blocks horizontally and about two floors vertically,
    // and only if seen, chasing the player, or right next to them on the same level.
    private static final double THREAT_RADIUS = 18;
    private static final double THREAT_VERTICAL = 6;
    private static final double CLOSE_DISTANCE_SQR = 5 * 5;
    private static final double CLOSE_VERTICAL = 2;
    // A monster seen recently keeps counting, so losing sight of a chaser around a corner is not an escape.
    private static final long SEEN_MEMORY_TICKS = 100;
    // Relief needs this many seconds in a row without a threat.
    private static final int ESCAPE_CONFIRM_SECONDS = 3;
    private static final Map<ServerPlayer, Map<Integer, Long>> LAST_SEEN = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> CLEAR_SECONDS = new WeakHashMap<>();
    // Last threat breakdown per player for /lm status: seen, chasing, close, remembered.
    private static final Map<ServerPlayer, int[]> LAST_THREATS = new WeakHashMap<>();
    // Apocalypse Now bleeding: normal is Bleeding 1, severe is Bleeding 2.
    private static final ResourceLocation BLEEDING = new ResourceLocation("apocalypsenow", "bleeding");
    private static final ResourceLocation SEVERE_BLEEDING = new ResourceLocation("apocalypsenow", "severebleeding");
    // Seeing your own blood keeps fear at least at Tense (Bleeding 1) or Fearful (Bleeding 2).
    private static final float[] BLEEDING_FEAR_FLOOR = {0, 34, 59};
    // The Hordes infection: the effect amplifier 0..3 is the stage, shown as Infected 1..4.
    private static final ResourceLocation INFECTED = new ResourceLocation("hordes", "infected");
    private static final ResourceLocation[] PAINKILLERS = {
            new ResourceLocation("legendarysurvivaloverhaul", "painkiller"),
            new ResourceLocation("apocalypsenow", "painkillerseffect")};
    // Relief gained when escaping from Tense/Fearful/Panicked; escaping without fear gives none.
    private static final float[] RELIEF_GAIN = {0, 20, 40, 70};
    // Fear recovery per second at Relieved 0..4.
    private static final float[] FEAR_RECOVERY = {5, 7, 8, 9, 10};
    // Sedatives: 3 min with no fear, then 5 min of withdrawal. Each dose within one game day of the previous one adds
    // tolerance (up to 4): the effect lasts 25% less and the withdrawal 50% longer per point.
    private static final int SEDATED_SECONDS = 180;
    private static final int WITHDRAWAL_SECONDS = 300;
    private static final int MAX_TOLERANCE = 4;
    private static final long TOLERANCE_WINDOW_TICKS = 24000;
    private final float[] values = new float[KEYS.length];
    private int sedatedSeconds;
    private int withdrawalSeconds;
    // Withdrawal length decided when the dose was taken, applied when the sedation ends.
    private int pendingWithdrawalSeconds;
    private int tolerance;
    private long lastDoseTime = Long.MIN_VALUE / 2;
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
        state.sedatedSeconds = tag.getInt("SedatedSeconds");
        state.withdrawalSeconds = tag.getInt("WithdrawalSeconds");
        state.pendingWithdrawalSeconds = tag.getInt("PendingWithdrawalSeconds");
        state.tolerance = tag.getInt("SedativeTolerance");
        if (tag.contains("LastDoseTime")) state.lastDoseTime = tag.getLong("LastDoseTime");
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
        tag.putInt("SedatedSeconds", sedatedSeconds);
        tag.putInt("WithdrawalSeconds", withdrawalSeconds);
        tag.putInt("PendingWithdrawalSeconds", pendingWithdrawalSeconds);
        tag.putInt("SedativeTolerance", tolerance);
        tag.putLong("LastDoseTime", lastDoseTime);
        tag.putLong("FootDistanceCm", footDistanceCm);
        tag.putLong("LastFootStatCm", lastFootStatCm);
        player.getPersistentData().put(TAG, tag);
    }

    void update(ServerPlayer player) {
        int nearby = countThreats(player);
        int light = player.serverLevel().getMaxLocalRawBrightness(player.blockPosition());
        float health = player.getHealth() / player.getMaxHealth();
        boolean threatened = nearby > 0;
        int panicBonus = (int) Courage.value(player, Courage.PANIC_THRESHOLD);
        boolean critical = nearby >= 6 + panicBonus || nearby >= 3 + panicBonus / 2 && health < 0.5f;
        boolean unsafe = nearby >= 3 || health < 0.45f;
        boolean dark = light < 4 && Courage.value(player, Courage.DARK_IMMUNITY) < 0.5;
        float target = critical ? 84 : unsafe ? 59 : threatened || dark ? 34 : 0;
        target = Math.max(target, BLEEDING_FEAR_FLOOR[bleeding(player)]);
        // The last infection stage: knowing you are about to turn keeps fear at least at Tense.
        if (infected(player) >= 4) target = Math.max(target, 34);
        float riseRate = 1 - Courage.fearResistance(player);
        int forcedRelief = testLevel(player, 2);
        int relief = Math.min(4, forcedRelief >= 0 ? forcedRelief : level(values[1]));
        tickSedatives();
        boolean sedated = effective(player, 4, sedatedSeconds);
        boolean withdrawal = effective(player, 5, withdrawalSeconds);
        // Withdrawal: rebound anxiety makes fear rise 50% faster and fade 2 points per second slower.
        if (withdrawal) riseRate *= 1.5f;
        float recovery = FEAR_RECOVERY[relief] + (float) Courage.value(player, Courage.FEAR_RECOVERY) - (withdrawal ? 2 : 0);
        float fearBefore = values[0];
        if (sedated) {
            // Sedated: fear drains within a couple of seconds and cannot rise; no peak means no relief or courage XP.
            values[0] = Math.max(0, values[0] - 50);
            peakFear = 0;
        } else if (values[0] < target) {
            values[0] = Math.min(target, values[0] + (critical ? 10 : unsafe ? 9 : 8) * riseRate);
        } else {
            values[0] = Math.max(target, values[0] - Math.max(1, recovery));
        }
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
        // wasThreatened stays set until the escape is confirmed by several seconds without any threat.
        int clear = threatened ? 0 : CLEAR_SECONDS.getOrDefault(player, 0) + 1;
        CLEAR_SECONDS.put(player, clear);
        if (threatened) {
            wasThreatened = true;
            move(1, -8);
        } else if (wasThreatened && clear >= ESCAPE_CONFIRM_SECONDS) {
            int escapedStage = Math.min(3, level(peakFear));
            move(1, RELIEF_GAIN[escapedStage]);
            Courage.onEscape(player, escapedStage);
            peakFear = 0;
            wasThreatened = false;
        } else {
            move(1, -2);
        }
    }

    private static int countThreats(ServerPlayer player) {
        long now = player.level().getGameTime();
        Map<Integer, Long> seenAt = LAST_SEEN.computeIfAbsent(player, ignored -> new HashMap<>());
        int[] kinds = new int[4];
        int count = 0;
        for (Monster monster : player.serverLevel().getEntitiesOfClass(Monster.class,
                player.getBoundingBox().inflate(THREAT_RADIUS, THREAT_VERTICAL, THREAT_RADIUS), Monster::isAlive)) {
            double dy = Math.abs(monster.getY() - player.getY());
            int kind;
            if (player.hasLineOfSight(monster)) {
                seenAt.put(monster.getId(), now);
                kind = 0;
            } else if (monster.getTarget() == player) {
                kind = 1;
            } else if (monster.distanceToSqr(player) <= CLOSE_DISTANCE_SQR && dy <= CLOSE_VERTICAL) {
                kind = 2;
            } else if (now - seenAt.getOrDefault(monster.getId(), Long.MIN_VALUE / 2) <= SEEN_MEMORY_TICKS) {
                kind = 3;
            } else {
                continue;
            }
            kinds[kind]++;
            count++;
        }
        seenAt.values().removeIf(time -> now - time > SEEN_MEMORY_TICKS);
        LAST_THREATS.put(player, kinds);
        return count;
    }

    static int[] lastThreats(ServerPlayer player) {
        return LAST_THREATS.getOrDefault(player, new int[4]);
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

    void onHurt(ServerPlayer player, float damage) {
        if (effective(player, 4, sedatedSeconds)) return;
        float courage = (1 - Courage.fearResistance(player))
                * (1 - (float) Math.min(1, Courage.value(player, Courage.FEAR_DAMAGE_RESISTANCE)));
        values[0] = Math.min(84, values[0] + Math.min(30, 10 + damage * 3) * courage);
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
        levels[4] = (byte) (sedatedSeconds > 0 ? 1 : 0);
        levels[5] = (byte) (withdrawalSeconds > 0 ? 1 : 0);
        levels[6] = (byte) naturalBleeding(player);
        levels[7] = (byte) naturalInfected(player);
        // The crash after sedatives adds one temporary Exhausted level.
        if (effective(player, 5, withdrawalSeconds)) levels[1] = (byte) Math.min(5, levels[1] + 1);
        for (int i = 0; i < COUNT; i++) {
            int forced = testLevel(player, i);
            if (forced >= 0) levels[i] = (byte) forced;
        }
        return levels;
    }

    static int bleeding(ServerPlayer player) {
        int forced = testLevel(player, 6);
        return forced >= 0 ? forced : naturalBleeding(player);
    }

    private static int naturalBleeding(Player player) {
        if (hasEffect(player, SEVERE_BLEEDING)) return 2;
        return hasEffect(player, BLEEDING) ? 1 : 0;
    }

    static int infected(ServerPlayer player) {
        int forced = testLevel(player, 7);
        return forced >= 0 ? forced : naturalInfected(player);
    }

    private static int naturalInfected(Player player) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(INFECTED);
        MobEffectInstance instance = effect == null ? null : player.getEffect(effect);
        return instance == null ? 0 : Math.min(4, instance.getAmplifier() + 1);
    }

    private static boolean hasEffect(Player player, ResourceLocation id) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
        return effect != null && player.hasEffect(effect);
    }

    private void tickSedatives() {
        if (sedatedSeconds > 0 && --sedatedSeconds == 0) {
            withdrawalSeconds = pendingWithdrawalSeconds;
            pendingWithdrawalSeconds = 0;
        } else if (withdrawalSeconds > 0) {
            withdrawalSeconds--;
        }
    }

    private static boolean effective(ServerPlayer player, int index, int seconds) {
        int forced = testLevel(player, index);
        return forced >= 0 ? forced > 0 : seconds > 0;
    }

    // A dose (also during withdrawal, which it ends) restarts the sedation; tolerance grows with frequent use.
    void takeSedative(ServerPlayer player) {
        long now = player.level().getGameTime();
        tolerance = now - lastDoseTime < TOLERANCE_WINDOW_TICKS ? Math.min(MAX_TOLERANCE, tolerance + 1) : 0;
        lastDoseTime = now;
        sedatedSeconds = (int) Math.round(SEDATED_SECONDS * Math.pow(0.75, tolerance));
        pendingWithdrawalSeconds = (int) Math.round(WITHDRAWAL_SECONDS * Math.pow(1.5, tolerance));
        withdrawalSeconds = 0;
    }

    static void markAcutePain(ServerPlayer player) {
        ACUTE_PAIN_UNTIL.put(player, player.level().getGameTime() + ACUTE_PAIN_TICKS);
    }

    static boolean onPainkillers(Player player) {
        for (ResourceLocation id : PAINKILLERS) {
            if (hasEffect(player, id)) return true;
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
