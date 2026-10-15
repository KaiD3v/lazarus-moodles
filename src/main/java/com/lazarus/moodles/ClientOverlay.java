package com.lazarus.moodles;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LazarusMoodles.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientOverlay {
    private static final ResourceLocation ICONS = new ResourceLocation(LazarusMoodles.ID, "textures/gui/moodles.png");
    private static final String[] KEYS = {"tense", "fearful", "panicked", "pain", "exhausted", "relieved"};
    // Background color per level for fear, exhausted, relieved, pain: bad moodles go yellow to red, Relieved goes green.
    private static final int[][] COLORS = {
            {0xC9A23A, 0xD9702E, 0xC0392B},
            {0x8A8778, 0xC9A23A, 0xD08A34, 0xD9702E, 0xC0392B},
            {0x6E8F5A, 0x5FA052, 0x4FB04A, 0x3FC042},
            {0xC9A23A, 0xD9702E, 0xC0392B}};
    private static final int ICON = 20;
    private static final int SPACING = 24;
    // A moodle that appears or changes level is shown at full opacity with its name for this long, then fades.
    private static final long FRESH_MS = 2500;
    private static final long FADE_MS = 500;
    private static final float STABLE_ALPHA = 0.65f;
    private static byte[] levels = new byte[MoodState.COUNT];
    private static final long[] changedAt = new long[MoodState.COUNT];

    private ClientOverlay() {}

    static void setLevels(byte[] next) {
        long now = Util.getMillis();
        for (int i = 0; i < next.length; i++) {
            if (next[i] > 0 && next[i] != levels[i]) changedAt[i] = now;
        }
        levels = next.clone();
    }

    static int level(int index) {
        return levels[index];
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("fear_vignette", (gui, graphics, partialTick, width, height) ->
                ClientEffects.renderOverlay(graphics, partialTick, width, height));
        event.registerAboveAll("moodles", (gui, graphics, partialTick, width, height) -> render(graphics, width, height));
    }

    private static void render(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int active = 0;
        for (byte level : levels) if (level > 0) active++;
        int y = Math.max(20, height / 2 - active * SPACING / 2);
        int x = width - ICON - 6;
        long now = Util.getMillis();
        RenderSystem.enableBlend();
        for (int i = 0; i < levels.length; i++) {
            int level = levels[i];
            if (level == 0) continue;
            int icon = i == 0 ? level - 1 : i == 3 ? 3 : i + 3;
            float fade = Mth.clamp((now - changedAt[i] - FRESH_MS) / (float) FADE_MS, 0, 1);
            float alpha = Mth.lerp(fade, 1.0f, STABLE_ALPHA);
            int color = COLORS[i][Math.min(level, COLORS[i].length) - 1];
            graphics.fill(x - 1, y - 1, x + ICON + 1, y + ICON + 1, (int) (0x90 * alpha) << 24 | color);
            graphics.setColor(1, 1, 1, alpha);
            graphics.blit(ICONS, x, y, ICON, ICON, (float) ((icon % 3) * 512),
                    (float) ((icon / 3) * 512), 512, 512, 1536, 1024);
            graphics.setColor(1, 1, 1, 1);
            // Text with alpha below 4 is drawn fully opaque by Minecraft, so faded labels are skipped instead.
            int labelAlpha = (int) (255 * (1 - fade));
            if (labelAlpha >= 8) {
                String text = Component.translatable("moodle.lazarusmoodles." + KEYS[icon]).getString();
                graphics.drawString(mc.font, text, x - 5 - mc.font.width(text), y + (ICON - 8) / 2,
                        labelAlpha << 24 | 0xE3DCC8, true);
            }
            y += SPACING;
        }
    }
}
