package com.bettercontent.dimensiondrink.runtime.player

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item

class DimensionalCollarItem(val tier: Int) : Item(Properties().stacksTo(16)) {
    override fun interactLivingEntity(stack: net.minecraft.world.item.ItemStack, player: Player, target: LivingEntity, hand: InteractionHand): InteractionResult {
        if (player.level().isClientSide) return InteractionResult.SUCCESS
        val accepted = FontCompanions.mark(player, target, tier)
        if (accepted && !player.abilities.instabuild) stack.shrink(1)
        return if (accepted) InteractionResult.CONSUME else InteractionResult.FAIL
    }
}
