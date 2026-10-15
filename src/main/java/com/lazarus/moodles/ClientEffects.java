package com.lazarus.moodles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
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
    private static LocalPlayer lastPlayer;
    private static float yawOffset;
    private static float pitchOffset;
    private static int unsettledTicks;
    private static int breathCooldown;

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
            yawOffset = pitchOffset = 0;
            unsettledTicks = breathCooldown = 0;
        }
        if (player == null) return;

        int fear = player.isCreative() || player.isSpectator() ? 0 : ClientOverlay.level(0);
        boolean aiming = mc.screen == null && isAiming(player);
        if (player.isSprinting() || player.hurtTime > 0) unsettledTicks = fear * 15;
        else if (unsettledTicks > 0) unsettledTicks--;

        float strength = fear * 0.10f;
        if (unsettledTicks > 0) strength *= 1.0f + 0.5f * unsettledTicks / 45f;
        if (ClientOverlay.level(2) > 0) strength *= 0.6f;
        float nextYaw = aiming ? Mth.sin(player.tickCount * 0.21f) * strength : 0;
        float nextPitch = aiming ? Mth.cos(player.tickCount * 0.17f) * strength * 0.7f : 0;
        player.setYRot(player.getYRot() + nextYaw - yawOffset);
        player.setXRot(Mth.clamp(player.getXRot() + nextPitch - pitchOffset, -90, 90));
        yawOffset = nextYaw;
        pitchOffset = nextPitch;

        if (breathCooldown > 0) breathCooldown--;
        if (fear >= 2 && breathCooldown == 0 && mc.screen == null && !player.isCreative()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(LazarusMoodles.PANICKED_BREATH.get(),
                    0.95f + fear * 0.02f, fear == 3 ? 0.25f : 0.16f));
            breathCooldown = fear == 3 ? 65 : 95;
        }
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
