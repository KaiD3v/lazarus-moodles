package com.lazarus.moodles;

import net.minecraft.server.level.ServerPlayer;

// Read-only API for other mods (Lazarus Stealth). Values are the effective levels the HUD shows,
// including temporary bonuses such as the sedative crash.
public final class LazarusMoodlesApi {
    private LazarusMoodlesApi() {}

    // Exhausted level 0..5.
    public static int exhaustionLevel(ServerPlayer player) {
        return MoodState.load(player).levels(player)[1];
    }
}
