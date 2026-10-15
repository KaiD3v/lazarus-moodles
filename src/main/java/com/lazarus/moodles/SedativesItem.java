package com.lazarus.moodles;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

// One dose: Sedated (no fear) for 3 minutes, then Withdrawal. See MoodState.takeSedative.
final class SedativesItem extends Item {
    private static final int USE_TICKS = 24;
    private static final int COOLDOWN_TICKS = 40;

    SedativesItem() {
        super(new Item.Properties().stacksTo(8));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            MoodState state = MoodState.load(player);
            state.takeSedative(player);
            state.save(player);
            byte[] levels = state.levels(player);
            MoodEffects.applyModifiers(player, levels);
            LazarusMoodles.sync(player, state);
        }
        if (entity instanceof Player player) {
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lazarusmoodles.sedatives.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lazarusmoodles.sedatives.desc2").withStyle(ChatFormatting.DARK_RED));
    }
}
