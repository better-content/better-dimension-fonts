package com.bettercontent.dimensiondrink.runtime.player

import com.bettercontent.dimensiondrink.runtime.backend.RunBackendManager
import com.bettercontent.dimensiondrink.runtime.run.RunRegistry
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent

object PlayerReturnHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onPlayerTick(event: TickEvent.PlayerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val player = event.player as? ServerPlayer ?: return
        if (player.level().isClientSide) return

        val run = RunRegistry.getRun(player.uuid) ?: return
        val record = RunRegistry.get(run.runId)

        if (record == null) {
            RunRegistry.clearPlayerAssignment(player.server, player.uuid)
            RunBackendManager.backend.clearPlayer(player.uuid)
            return
        }

        if (player.uuid in record.pendingPlayers) {
            return
        }

        // Location is not an exit condition. A traveler may explore beyond the site
        // bounds without losing the Font binding or being returned to the origin.
    }
}
