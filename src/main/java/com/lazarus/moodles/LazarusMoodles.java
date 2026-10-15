package com.lazarus.moodles;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

@Mod(LazarusMoodles.ID)
public class LazarusMoodles {
    public static final String ID = "lazarusmoodles";
    private static final String VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ID, "moodles"), () -> VERSION, VERSION::equals, VERSION::equals);

    public LazarusMoodles() {
        CHANNEL.registerMessage(0, MoodPacket.class, MoodPacket::encode, MoodPacket::decode,
                MoodPacket::handle, OptionalDirection.CLIENTBOUND);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) return;
        MoodState state = MoodState.load(player);
        state.update(player);
        state.save(player);
        sync(player, state);
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        MoodState state = MoodState.load(player);
        state.onHurt(event.getAmount());
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

    private static void sync(ServerPlayer player, MoodState state) {
        CHANNEL.sendTo(new MoodPacket(state.levels()), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
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
