package com.lazarus.moodles;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;
import java.util.WeakHashMap;

// Courage skill tree support: attributes granted by Puffish Skills rewards, and courage XP for that tree.
// Without Puffish Skills every attribute stays 0 and fear behaves as before.
final class Courage {
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, LazarusMoodles.ID);
    // Fraction by which fear rises more slowly; capped so a horde still causes panic.
    static final RegistryObject<Attribute> FEAR_RESISTANCE = register("fear_resistance", 1);
    static final RegistryObject<Attribute> FEAR_DAMAGE_RESISTANCE = register("fear_damage_resistance", 1);
    // 1 = darkness alone no longer causes fear.
    static final RegistryObject<Attribute> DARK_IMMUNITY = register("dark_immunity", 1);
    // Extra fear recovered per second.
    static final RegistryObject<Attribute> FEAR_RECOVERY = register("fear_recovery", 10);
    // Fraction by which fear aim sway and reload slowdown are weakened.
    static final RegistryObject<Attribute> FEAR_STEADINESS = register("fear_steadiness", 1);
    // Extra monsters needed before Panicked.
    static final RegistryObject<Attribute> PANIC_THRESHOLD = register("panic_threshold", 10);
    static final float MAX_FEAR_RESISTANCE = 0.35f;

    private static final String CATEGORY = "puffish_skills:coragem";
    private static final int ESCAPE_COOLDOWN_TICKS = 2400;
    private static final Map<ServerPlayer, Long> LAST_ESCAPE_XP = new WeakHashMap<>();

    private Courage() {}

    private static RegistryObject<Attribute> register(String name, double max) {
        return ATTRIBUTES.register(name, () -> new RangedAttribute(
                "attribute.name." + LazarusMoodles.ID + "." + name, 0, 0, max).setSyncable(true));
    }

    static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener((EntityAttributeModificationEvent event) -> {
            for (RegistryObject<Attribute> attribute : ATTRIBUTES.getEntries()) event.add(EntityType.PLAYER, attribute.get());
        });
    }

    static double value(Player player, RegistryObject<Attribute> attribute) {
        AttributeInstance instance = player == null ? null : player.getAttribute(attribute.get());
        return instance == null ? 0 : instance.getValue();
    }

    static float fearResistance(Player player) {
        return (float) Math.min(MAX_FEAR_RESISTANCE, value(player, FEAR_RESISTANCE));
    }

    // Escaping while Fearful gives 3 XP and while Panicked 8; Tense escapes give none because they are easy to farm.
    static void onEscape(ServerPlayer player, int fearStage) {
        int amount = fearStage >= 3 ? 8 : fearStage == 2 ? 3 : 0;
        long now = player.level().getGameTime();
        Long last = LAST_ESCAPE_XP.get(player);
        if (amount == 0 || last != null && now - last < ESCAPE_COOLDOWN_TICKS) return;
        LAST_ESCAPE_XP.put(player, now);
        grant(player, amount);
    }

    static void grant(ServerPlayer player, int amount) {
        if (player.isCreative() || player.isSpectator() || !ModList.get().isLoaded("puffish_skills")) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                "puffish_skills experience add " + player.getGameProfile().getName() + " " + CATEGORY + " " + amount);
    }
}
