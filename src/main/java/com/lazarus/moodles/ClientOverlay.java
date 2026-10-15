package com.lazarus.moodles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LazarusMoodles.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientOverlay {
    private static final ResourceLocation ICONS = new ResourceLocation(LazarusMoodles.ID, "textures/gui/moodles.png");
    private static final String[] KEYS = {"tense", "fearful", "panicked", "irritated", "exhausted", "relieved"};
    private static byte[] levels = new byte[MoodState.COUNT];

    private ClientOverlay() {}

    static void setLevels(byte[] next) {
        levels = next.clone();
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("moodles", (gui, graphics, partialTick, width, height) -> render(graphics, width, height));
    }

    private static void render(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int active = 0;
        for (byte level : levels) if (level > 0) active++;
        int y = Math.max(20, height / 2 - active * 14);
        int x = width - 29;
        for (int i = 0; i < levels.length; i++) {
            int level = levels[i];
            if (level == 0) continue;
            graphics.fill(x - 2, y - 2, x + 26, y + 26, 0xB0101511);
            graphics.blit(ICONS, x, y, 24, 24, (float) ((i % 3) * 512),
                    (float) ((i / 3) * 512), 512, 512, 1536, 1024);
            int color = level == 4 ? 0xE06645 : level == 3 ? 0xD89A4C : 0xB8A675;
            for (int tick = 0; tick < level; tick++) graphics.fill(x - 3, y + 20 - tick * 5, x - 1, y + 23 - tick * 5, 0xFF000000 | color);
            Component label = Component.translatable("moodle.lazarusmoodles." + KEYS[i]);
            String text = label.getString() + " " + level;
            int labelX = x - 7 - mc.font.width(text);
            graphics.drawString(mc.font, text, labelX, y + 8, 0xFFE3DCC8, true);
            y += 28;
        }
    }
}
