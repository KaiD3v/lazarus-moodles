package com.lazarus.moodles;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

@Mod(LazarusMoodles.ID)
public class LazarusMoodles {
    public static final String ID = "lazarusmoodles";
    private static final String VERSION = "15";
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    static final RegistryObject<Item> SEDATIVES = ITEMS.register("sedatives", SedativesItem::new);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ID);
    static final RegistryObject<SoundEvent> PANICKED_BREATH = SOUNDS.register("panicked_breath",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "panicked_breath")));
    static final RegistryObject<SoundEvent> PAIN_GRUNT = SOUNDS.register("pain_grunt",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "pain_grunt")));
    static final RegistryObject<SoundEvent> TINNITUS = SOUNDS.register("tinnitus",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "tinnitus")));
    static final RegistryObject<SoundEvent> YAWN = SOUNDS.register("yawn",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "yawn")));
    static final RegistryObject<SoundEvent> EXHAUSTED_PANT = SOUNDS.register("exhausted_pant",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "exhausted_pant")));
    static final RegistryObject<SoundEvent> RELIEF_SIGH = SOUNDS.register("relief_sigh",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "relief_sigh")));
    static final RegistryObject<SoundEvent> CALM_BREATH = SOUNDS.register("calm_breath",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "calm_breath")));
    static final RegistryObject<SoundEvent> BLOOD_DRIP = SOUNDS.register("blood_drip",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "blood_drip")));
    static final RegistryObject<SoundEvent> COUGH = SOUNDS.register("cough",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "cough")));
    static final RegistryObject<SoundEvent> GAG = SOUNDS.register("gag",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "gag")));
    static final RegistryObject<SoundEvent> HEARTBEAT = SOUNDS.register("heartbeat",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "heartbeat")));
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ID, "moodles"), () -> VERSION, VERSION::equals, VERSION::equals);

    public LazarusMoodles() {
        SOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) event.accept(SEDATIVES);
        });
        Courage.register(FMLJavaModLoadingContext.get().getModEventBus());
        CHANNEL.registerMessage(0, MoodPacket.class, MoodPacket::encode, MoodPacket::decode,
                MoodPacket::handle, OptionalDirection.CLIENTBOUND);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        MoodEffects.slowReload(player);
        if (player.tickCount % 20 != 0) return;
        MoodState state = MoodState.load(player);
        state.update(player);
        state.save(player);
        MoodEffects.apply(player, state.levels(player));
        sync(player, state);
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        MoodState state = MoodState.load(player);
        state.onHurt(player, event.getAmount());
        MoodState.markAcutePain(player);
        state.save(player);
        sync(player, state);
    }

    // Killing a monster while Fearful or Panicked builds courage.
    @SubscribeEvent
    public void onKill(LivingDeathEvent event) {
        if (event.getEntity() instanceof Monster && event.getSource().getEntity() instanceof ServerPlayer player
                && MoodEffects.fear(player) >= 2) {
            Courage.grant(player, 1);
        }
    }

    @SubscribeEvent
    public void onWakeUp(PlayerWakeUpEvent event) {
        // wakeImmediately is false only when the night was skipped; leaving the bed early does not count.
        if (!(event.getEntity() instanceof ServerPlayer player) || event.wakeImmediately()
                || player.getSleepTimer() < 100) return;
        MoodState state = MoodState.load(player);
        state.onSleep(player);
        state.save(player);
        sync(player, state);
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        Player oldPlayer = event.getOriginal();
        oldPlayer.reviveCaps();
        if (oldPlayer.getPersistentData().contains(MoodState.TAG)) {
            event.getEntity().getPersistentData().put(MoodState.TAG,
                    oldPlayer.getPersistentData().getCompound(MoodState.TAG).copy());
        }
        oldPlayer.invalidateCaps();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player, MoodState.load(player));
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        MoodleCommands.register(event.getDispatcher());
    }

    static void sync(ServerPlayer player, MoodState state) {
        CHANNEL.sendTo(new MoodPacket(state.levels(player)), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    private record MoodPacket(byte[] levels) {
        private static void encode(MoodPacket packet, FriendlyByteBuf buf) {
            for (byte level : packet.levels) buf.writeByte(level);
        }

        private static MoodPacket decode(FriendlyByteBuf buf) {
            byte[] levels = new byte[MoodState.COUNT];
            for (int i = 0; i < levels.length; i++) levels[i] = buf.readByte();
            return new MoodPacket(levels);
        }

        private static void handle(MoodPacket packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientOverlay.setLevels(packet.levels)));
            context.get().setPacketHandled(true);
        }
    }

    private static final class OptionalDirection {
        private static final java.util.Optional<NetworkDirection> CLIENTBOUND =
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT);
    }
}
