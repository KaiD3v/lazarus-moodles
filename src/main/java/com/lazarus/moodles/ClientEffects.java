package com.lazarus.moodles;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

@Mod.EventBusSubscriber(modid = LazarusMoodles.ID, value = Dist.CLIENT)
public final class ClientEffects {
    private static final Method TACZ_OPERATOR = method(
            "com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator", "fromLocalPlayer", LocalPlayer.class);
    private static final Method TACZ_IS_AIMING = method(
            "com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator", "isAim");
    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");
    // Indexed by fear stage: none, Tense, Fearful, Panicked.
    private static final float[] SWAY_DEGREES = {0, 0.35f, 0.9f, 1.8f};
    private static final float[] TREMOR_DEGREES = {0, 0, 0.12f, 0.3f};
    private static final int[] BEAT_TICKS = {0, 0, 14, 9};
    private static final float[] BEAT_VOLUME = {0, 0, 0.45f, 0.8f};
    private static final float[] VIGNETTE_STRENGTH = {0, 0.25f, 0.5f, 0.75f};
    // Indexed by Exhausted level 0..5.
    private static final float[] EXHAUSTED_SWAY_DEGREES = {0, 0, 0, 0, 0.3f, 0.5f};
    // Indexed by Relieved level 0..4: how much calmer the aim gets.
    private static final float[] RELIEVED_STEADY = {0, 0.2f, 0.3f, 0.4f, 0.5f};
    private static final int CALM_BEAT_TICKS = 140;
    // Kept low (about half of Panicked) so dark nights stay playable.
    private static final float[] TIRED_VIGNETTE_STRENGTH = {0, 0, 0, 0.15f, 0.25f, 0.35f};
    private static LocalPlayer lastPlayer;
    private static float yawOffset;
    private static float pitchOffset;
    private static int unsettledTicks;
    private static int breathCooldown;
    private static int beatCooldown;
    private static int ticksSinceBeat = 100;
    private static float shownFear;
    private static float shownTired;
    private static int lastRelief;
    private static int reliefTicks = 200;
    private static int sighCooldown;
    private static int calmBreathsLeft;
    private static int calmBreathCooldown;
    private static int calmBeatTicks;
    private static int lastBeatInterval;
    private static float lastBeatVolume;
    private static float lastHealth;
    private static float hitStrength;
    private static int ticksSinceHit = 100;
    private static int gruntCooldown;
    private static int tinnitusCooldown;
    private static int blinkTicks = -1;
    private static int blinkLength;
    private static int nextBlink;
    private static boolean microsleep;
    private static int yawnCooldown;
    private static int sprintTicks;
    private static int pantCooldown;

    private ClientEffects() {}

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        int pain = ClientOverlay.level(3);
        if (pain > 0 && event.getEntity().onClimbable() && event.getInput().forwardImpulse > 0) {
            event.getInput().forwardImpulse *= 1.0f - 0.08f * pain;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player != lastPlayer) {
            lastPlayer = player;
            yawOffset = pitchOffset = shownFear = shownTired = 0;
            lastRelief = sighCooldown = calmBreathsLeft = calmBeatTicks = 0;
            reliefTicks = 200;
            unsettledTicks = breathCooldown = beatCooldown = 0;
            ticksSinceBeat = ticksSinceHit = 100;
            gruntCooldown = tinnitusCooldown = sprintTicks = pantCooldown = 0;
            blinkTicks = -1;
            nextBlink = 200;
            yawnCooldown = 400;
            lastHealth = player == null ? 0 : player.getHealth();
        }
        if (player == null || mc.isPaused()) return;

        float health = player.getHealth();
        if (health < lastHealth - 0.01f && !player.isCreative() && !player.isSpectator()) {
            onHit(mc, player, lastHealth - health);
        }
        lastHealth = health;
        ticksSinceHit++;
        if (gruntCooldown > 0) gruntCooldown--;
        if (tinnitusCooldown > 0) tinnitusCooldown--;

        int fear = fear(player);
        int relief = relieved(player);
        // Relief clears the red vignette faster.
        shownFear += Mth.clamp(fear - shownFear, relief > 0 ? -0.05f : -0.02f, 0.05f);
        boolean aiming = mc.screen == null && isAiming(player);
        if (player.isSprinting() || player.hurtTime > 0) unsettledTicks = fear * 15;
        else if (unsettledTicks > 0) unsettledTicks--;

        int exhausted = exhausted(player);
        shownTired += Mth.clamp(TIRED_VIGNETTE_STRENGTH[exhausted] - shownTired, -0.005f, 0.005f);
        // Courage (Mãos Firmes) weakens only the fear part of the sway.
        float steady = 1.0f - (float) Math.min(1, Courage.value(player, Courage.FEAR_STEADINESS));
        float strength = SWAY_DEGREES[fear] * steady + EXHAUSTED_SWAY_DEGREES[exhausted];
        if (unsettledTicks > 0) strength *= 1.0f + 0.6f * unsettledTicks / 45f;
        float tremor = TREMOR_DEGREES[fear] * steady;
        strength *= 1.0f - RELIEVED_STEADY[relief];
        tremor *= 1.0f - RELIEVED_STEADY[relief];
        int t = player.tickCount;
        float nextYaw = aiming ? Mth.sin(t * 0.21f) * strength + Mth.sin(t * 1.9f) * tremor : 0;
        float nextPitch = aiming ? Mth.cos(t * 0.17f) * strength * 0.7f + Mth.cos(t * 2.3f) * tremor : 0;
        player.setYRot(player.getYRot() + nextYaw - yawOffset);
        player.setXRot(Mth.clamp(player.getXRot() + nextPitch - pitchOffset, -90, 90));
        yawOffset = nextYaw;
        pitchOffset = nextPitch;

        ticksSinceBeat++;
        if (breathCooldown > 0) breathCooldown--;
        if (beatCooldown > 0) beatCooldown--;
        if (fear >= 2 && breathCooldown == 0) {
            // sounds.json picks one of four takes; random pitch, volume and gaps keep it from sounding looped.
            RandomSource random = player.getRandom();
            float pitch = (fear == 3 ? 1.03f : 0.95f) + (random.nextFloat() - 0.5f) * 0.14f;
            float volume = (fear == 3 ? 0.85f : 0.5f) * (0.85f + random.nextFloat() * 0.3f);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(LazarusMoodles.PANICKED_BREATH.get(), pitch, volume));
            // The longest take is 3 s (60 ticks), so Panicked always leaves at least half a second of silence.
            breathCooldown = fear == 3 ? 70 + random.nextInt(61) : 120 + random.nextInt(121);
        }
        if (BEAT_TICKS[fear] > 0 && beatCooldown == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(LazarusMoodles.HEARTBEAT.get(),
                    fear == 3 ? 1.1f : 1.0f, BEAT_VOLUME[fear]));
            beatCooldown = lastBeatInterval = BEAT_TICKS[fear];
            lastBeatVolume = BEAT_VOLUME[fear];
            calmBeatTicks = CALM_BEAT_TICKS;
            ticksSinceBeat = 0;
        } else if (BEAT_TICKS[fear] == 0 && calmBeatTicks > 0) {
            // When fear fades, the heart keeps beating for a few seconds, slowing down and getting quieter.
            calmBeatTicks--;
            if (beatCooldown == 0) {
                float progress = 1.0f - (float) calmBeatTicks / CALM_BEAT_TICKS;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(LazarusMoodles.HEARTBEAT.get(),
                        1.0f - 0.1f * progress, lastBeatVolume * (1.0f - progress)));
                beatCooldown = Math.round(Mth.lerp(progress, lastBeatInterval, 24));
            }
        }
        tickRelief(mc, player, relief, fear);
        tickExhaustion(mc, player, exhausted, fear);
    }

    // A sigh when relief rises, then one or two slow calming breaths.
    private static void tickRelief(Minecraft mc, LocalPlayer player, int relief, int fear) {
        RandomSource random = player.getRandom();
        if (reliefTicks < 200) reliefTicks++;
        if (sighCooldown > 0) sighCooldown--;
        // The tick check skips the saved Relieved level arriving right after joining a world.
        if (relief > lastRelief && sighCooldown == 0 && player.tickCount > 40) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(LazarusMoodles.RELIEF_SIGH.get(),
                    0.94f + random.nextFloat() * 0.12f, Math.min(1.0f, 0.45f + 0.12f * relief)));
            reliefTicks = 0;
            sighCooldown = 200;
            calmBreathsLeft = relief >= 2 ? 2 : 1;
            calmBreathCooldown = 60;
            // Panicked breathing stops instead of overlapping the sigh.
            breathCooldown = Math.max(breathCooldown, 200);
        }
        lastRelief = relief;
        if (calmBreathsLeft > 0 && --calmBreathCooldown <= 0) {
            if (fear < 2) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(LazarusMoodles.CALM_BREATH.get(),
                        0.95f + random.nextFloat() * 0.1f, 0.45f));
            }
            calmBreathsLeft--;
            calmBreathCooldown = 90;
        }
    }

    private static int relieved(LocalPlayer player) {
        return player.isCreative() || player.isSpectator() ? 0 : Math.min(4, ClientOverlay.level(2));
    }

    // Exhausted 3+: slow blinks and yawns. Exhausted 4+: panting after a sprint. Exhausted 5: rare microsleeps.
    private static void tickExhaustion(Minecraft mc, LocalPlayer player, int exhausted, int fear) {
        RandomSource random = player.getRandom();
        if (blinkTicks >= 0) {
            if (++blinkTicks > blinkLength) blinkTicks = -1;
        } else if (exhausted >= 3 && --nextBlink <= 0) {
            // Fear keeps the player awake, so microsleeps only happen when calm.
            microsleep = exhausted == 5 && fear == 0 && random.nextFloat() < 0.1f;
            blinkLength = microsleep ? 30 : 7;
            blinkTicks = 0;
            nextBlink = exhausted == 3 ? 400 + random.nextInt(400)
                    : exhausted == 4 ? 200 + random.nextInt(300) : 120 + random.nextInt(180);
        }

        if (yawnCooldown > 0) yawnCooldown--;
        if (exhausted >= 3 && fear < 2 && yawnCooldown == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(LazarusMoodles.YAWN.get(),
                    0.93f + random.nextFloat() * 0.14f, 0.55f * (0.85f + random.nextFloat() * 0.3f)));
            yawnCooldown = exhausted == 3 ? 1200 + random.nextInt(1200)
                    : exhausted == 4 ? 900 + random.nextInt(900) : 600 + random.nextInt(600);
        }

        if (pantCooldown > 0) pantCooldown--;
        if (player.isSprinting()) {
            sprintTicks++;
        } else {
            if (sprintTicks >= 60 && exhausted >= 4 && fear < 2 && pantCooldown == 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(LazarusMoodles.EXHAUSTED_PANT.get(),
                        0.95f + random.nextFloat() * 0.1f, exhausted == 5 ? 0.75f : 0.6f));
                pantCooldown = 200;
            }
            sprintTicks = 0;
        }
    }

    // How closed the eyes are, 0..1. A normal blink is a quick close and open; a microsleep closes, holds, then snaps open.
    private static float eyesClosed(float partialTick) {
        if (blinkTicks < 0) return 0;
        float t = Math.min(1, (blinkTicks + partialTick) / blinkLength);
        if (!microsleep) return Mth.sin(t * Mth.PI);
        return t < 0.25f ? t / 0.25f : t > 0.8f ? (1 - t) / 0.2f : 1;
    }

    private static int exhausted(LocalPlayer player) {
        return player.isCreative() || player.isSpectator() ? 0 : ClientOverlay.level(1);
    }

    // Pain feedback for one hit: grunt, camera flinch, gray flash and, on heavy hits at Pain 3, ringing ears.
    // Painkillers suppress all of it.
    private static void onHit(Minecraft mc, LocalPlayer player, float damage) {
        if (MoodState.onPainkillers(player)) return;
        int pain = ClientOverlay.level(3);
        RandomSource random = player.getRandom();
        hitStrength = Mth.clamp(damage / 6.0f, 0.25f, 1.0f) * (0.7f + 0.1f * pain);
        ticksSinceHit = 0;
        // Small repeated damage such as bleeding grunts only sometimes.
        if (gruntCooldown == 0 && (damage >= 1 || random.nextFloat() < 0.3f)) {
            float volume = Math.min(1.0f, 0.45f + 0.12f * pain + Math.min(0.25f, damage * 0.05f));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(LazarusMoodles.PAIN_GRUNT.get(),
                    0.92f + random.nextFloat() * 0.16f, volume));
            gruntCooldown = 12;
        }
        if (pain >= 3 && damage >= 3 && tinnitusCooldown == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(LazarusMoodles.TINNITUS.get(), 1.0f, 0.4f));
            tinnitusCooldown = 300;
        }
    }

    private static float flinch(float partialTick) {
        return hitStrength * Math.max(0, 1.0f - (ticksSinceHit + partialTick) / 8.0f);
    }

    // Narrows the view slightly on each heartbeat while Panicked. Scaled by the vanilla FOV effects slider.
    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        Minecraft mc = Minecraft.getInstance();
        float scale = mc.options.fovEffectScale().get().floatValue();
        // Relief: the view opens about 2% for a few seconds, the reverse of the Panicked squeeze.
        float t = reliefTicks + mc.getFrameTime();
        float open = t < 10 ? t / 10 : t < 40 ? 1 : Math.max(0, 1 - (t - 40) / 60);
        float modifier = 1.0f + 0.02f * open * scale;
        if (fear(mc.player) >= 3) modifier *= 1.0f - 0.025f * pulse(mc.getFrameTime()) * scale;
        event.setNewFovModifier(event.getNewFovModifier() * modifier);
    }

    // Small trembling camera roll while Panicked. Scaled by the vanilla distortion effects slider.
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float partialTick = (float) event.getPartialTick();
        float scale = mc.options.screenEffectScale().get().floatValue();
        float flinch = flinch(partialTick) * scale;
        if (flinch > 0) {
            // Head snaps away from the hit; only the camera moves, not the aim.
            float side = -Mth.sin(mc.player.getHurtDir() * Mth.DEG_TO_RAD);
            event.setPitch(event.getPitch() + 3.0f * flinch);
            event.setRoll(event.getRoll() + 5.0f * side * flinch);
        }
        if (microsleep && blinkTicks >= 0) {
            // The head drops while dozing off and jerks back up on waking.
            event.setPitch(event.getPitch() + 9.0f * eyesClosed(partialTick) * scale);
        }
        if (fear(mc.player) < 3) return;
        float time = mc.player.tickCount + partialTick;
        event.setRoll(event.getRoll() + (Mth.sin(time * 1.7f) * 0.5f + Mth.sin(time * 3.1f) * 0.25f) * scale);
    }

    static void renderOverlay(GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float scale = mc.options.screenEffectScale().get().floatValue();
        renderVignette(graphics, partialTick, width, height, scale);
        // Short pale flash on a hit, distinct from the red fear vignette.
        int flash = (int) (110 * flinch(partialTick) * Math.max(0.35f, scale));
        if (flash > 0) graphics.fill(0, 0, width, height, Math.min(255, flash) << 24 | 0xD8D4CC);
        // Eyelids closing from the top and bottom, with a soft edge.
        int lid = (int) ((height / 2 + 16) * eyesClosed(partialTick) * scale);
        if (lid > 0) {
            graphics.fill(0, 0, width, Math.max(0, lid - 16), 0xFF000000);
            graphics.fillGradient(0, lid - 16, width, lid, 0xFF000000, 0x00000000);
            graphics.fill(0, height - Math.max(0, lid - 16), width, height, 0xFF000000);
            graphics.fillGradient(0, height - lid, width, height - lid + 16, 0x00000000, 0xFF000000);
        }
    }

    // One vignette for fear (blood red, throbs with the heartbeat) and exhaustion (neutral dark, heavy eyelids).
    // Each color channel takes the stronger of the two, so they never stack into a black screen.
    private static void renderVignette(GuiGraphics graphics, float partialTick, int width, int height, float scale) {
        int stage = Mth.clamp(Mth.ceil(shownFear), 0, 3);
        float fear = 0;
        if (shownFear > 0.01f) {
            fear = Mth.lerp(shownFear - (stage - 1), VIGNETTE_STRENGTH[Math.max(0, stage - 1)], VIGNETTE_STRENGTH[stage]);
            fear = Math.min(0.95f, (fear + 0.15f * pulse(partialTick) * (stage >= 2 ? 1 : 0)) * Math.max(0.35f, scale));
        }
        float tired = 0;
        if (shownTired > 0.01f) {
            float time = Minecraft.getInstance().player.tickCount + partialTick;
            // Slow drooping of the eyelids, darker just before and during each blink.
            float preBlink = blinkTicks < 0 && nextBlink <= 12 ? (12 - nextBlink + partialTick) / 12 : 0;
            tired = shownTired + 0.04f * Mth.sin(time * 0.08f) + 0.12f * Math.min(1, preBlink) + 0.15f * eyesClosed(partialTick);
            tired = Mth.clamp(tired * Math.max(0.35f, scale), 0, 0.6f);
        }
        if (fear > 0 || tired > 0) {
            RenderSystem.enableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            // Fear darkens green and blue more than red, so its edges turn blood red; tiredness darkens all channels.
            graphics.setColor(Math.max(fear * 0.35f, tired), Math.max(fear, tired), Math.max(fear, tired), 1.0f);
            graphics.blit(VIGNETTE, 0, 0, -90, 0, 0, width, height, width, height);
            graphics.setColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
        if (stage == 3) {
            int alpha = (int) (40 * pulse(partialTick) * scale);
            if (alpha > 0) graphics.fill(0, 0, width, height, alpha << 24 | 0x500000);
        }
    }

    private static float pulse(float partialTick) {
        return Math.max(0, 1.0f - (ticksSinceBeat + partialTick) / 5.0f);
    }

    private static int fear(LocalPlayer player) {
        return player == null || player.isCreative() || player.isSpectator() ? 0 : ClientOverlay.level(0);
    }

    private static boolean isAiming(LocalPlayer player) {
        if (TACZ_OPERATOR != null && TACZ_IS_AIMING != null) {
            try {
                Object operator = TACZ_OPERATOR.invoke(null, player);
                if (operator != null && Boolean.TRUE.equals(TACZ_IS_AIMING.invoke(operator))) return true;
            } catch (ReflectiveOperationException ignored) {
                // Other ranged weapons still use vanilla aiming below.
            }
        }
        return player.isUsingItem() && (player.getUseItem().getItem() instanceof BowItem
                || player.getUseItem().getItem() instanceof CrossbowItem
                || player.getUseItem().getItem() instanceof TridentItem);
    }

    private static Method method(String className, String name, Class<?>... arguments) {
        try {
            return Class.forName(className).getMethod(name, arguments);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
