package com.bettercontent.dimensiondrink.content

import com.bettercontent.dimensiondrink.registry.ModBlocks
import net.minecraft.network.chat.Component
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

object FontHarvestEvents {
    @SubscribeEvent
    fun onBreak(event: BlockEvent.BreakEvent) {
        if (!event.state.`is`(ModBlocks.OBELISK.get())) return
        val font = event.level.getBlockEntity(event.pos) as? ObeliskBlockEntity ?: return
        if (!font.isRunActive()) return
        event.isCanceled = true
        event.player.displayClientMessage(Component.literal("The Font cannot be harvested during an expedition."), true)
    }
}
