package com.bettercontent.betterdimensionfonts.runtime.player

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

class DimensionalCollarItem(val tier: Int) : Item(Properties().stacksTo(16)) {
    override fun appendHoverText(stack: ItemStack, level: Level?, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip.add(Component.translatable("tooltip.better_dimension_fonts.collar.mark").withStyle(ChatFormatting.GRAY))
        tooltip.add(Component.translatable("tooltip.better_dimension_fonts.collar.travel").withStyle(ChatFormatting.DARK_GRAY))
    }

    override fun interactLivingEntity(stack: net.minecraft.world.item.ItemStack, player: Player, target: LivingEntity, hand: InteractionHand): InteractionResult {
        if (player.level().isClientSide) return InteractionResult.SUCCESS
        val accepted = FontCompanions.mark(player, target, tier)
        if (accepted && !player.abilities.instabuild) stack.shrink(1)
        return if (accepted) InteractionResult.CONSUME else InteractionResult.FAIL
    }
}
