package com.bettercontent.betterdimensionfonts.runtime.ui

import com.bettercontent.betterdimensionfonts.ObeliskConstants
import com.bettercontent.betterdimensionfonts.content.ObeliskBlockEntity
import com.bettercontent.betterdimensionfonts.runtime.run.RunRegistry
import com.bettercontent.betterdimensionfonts.runtime.CelestialIntegration
import com.bettercontent.betterdimensionfonts.runtime.run.RunState
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.BossEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.server.ServerStoppedEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import java.util.UUID

object RunBossBarManager {

    private val activeBossBars = linkedMapOf<UUID, ServerBossEvent>()
    private val dawnWarnings = mutableMapOf<UUID, Pair<Long, Int>>()

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }

        val server = event.server
        val openWays = CelestialIntegration.openWays(server)
        if (openWays == null) dawnWarnings.clear()
        val activeRuns = RunRegistry.currentRuns()
        val validRunIds = activeRuns.map { it.id }.toSet()

        activeRuns.forEach { run ->
            if (run.state != RunState.ACTIVE) {
                removeBossBar(run.id)
                return@forEach
            }

            val originLevel = run.originLevelKey?.let(server::getLevel)
            val obelisk = run.originObeliskPos?.let { pos -> originLevel?.getBlockEntity(pos) as? ObeliskBlockEntity }
            if (obelisk == null) {
                removeBossBar(run.id)
                return@forEach
            }

            val free = openWays != null && obelisk.isOpenWaysAvailable()
            if (free) {
                run.activePlayers.mapNotNull(server.playerList::getPlayer).forEach { player ->
                    val previous = dawnWarnings[player.uuid]?.takeIf { it.first == openWays!!.occurrenceId }?.second ?: 0
                    val stage = warningStage(openWays!!.remainingTicks)
                    if (stage > previous) {
                        player.sendSystemMessage(Component.literal(if (stage == 2)
                            "Dawn is close. Font charge drain will resume; empty Fonts will extract you safely."
                        else "Open Ways ends at the approaching dawn. Font charge drain will resume."))
                        dawnWarnings[player.uuid] = openWays.occurrenceId to stage
                    }
                }
            }
            val presentation = presentation(obelisk.getChargePercent(), free)
            if (presentation == null) {
                removeBossBar(run.id)
                return@forEach
            }

            val bossBar = activeBossBars.getOrPut(run.id) {
                ServerBossEvent(
                    Component.literal("Font Charge"),
                    BossEvent.BossBarColor.GREEN,
                    BossEvent.BossBarOverlay.PROGRESS
                )
            }

            bossBar.progress = presentation.progress
            bossBar.color = presentation.color
            bossBar.name = Component.literal(presentation.title)

            val livePlayers = run.activePlayers.mapNotNull(server.playerList::getPlayer).toSet()
            livePlayers.forEach { player ->
                if (!bossBar.players.contains(player)) {
                    bossBar.addPlayer(player)
                }
            }
            bossBar.players.toList()
                .filter { it !in livePlayers }
                .forEach(bossBar::removePlayer)
        }

        activeBossBars.keys
            .filter { it !in validRunIds }
            .toList()
            .forEach(::removeBossBar)
    }

    @SubscribeEvent
    fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        activeBossBars.values.forEach { it.removePlayer(player) }
    }

    fun hasBossBar(runId: UUID): Boolean = activeBossBars.containsKey(runId)

    internal fun bossBarProgress(runId: UUID): Float? = activeBossBars[runId]?.progress

    internal fun bossBarTitle(runId: UUID): String? = activeBossBars[runId]?.name?.string

    @SubscribeEvent
    fun onServerStopped(@Suppress("UNUSED_PARAMETER") event: ServerStoppedEvent) {
        activeBossBars.keys.toList().forEach(::removeBossBar)
        dawnWarnings.clear()
    }

    fun removeBossBar(runId: UUID) {
        activeBossBars.remove(runId)?.let { bossBar ->
            bossBar.players.toList().forEach(bossBar::removePlayer)
        }
    }

    internal fun warningStage(remainingTicks: Long): Int = when {
        remainingTicks in 1..200 -> 2
        remainingTicks in 201..1000 -> 1
        else -> 0
    }

    internal fun presentation(chargeFraction: Double, openWays: Boolean = false): FontBossBarPresentation? {
        if (openWays) return FontBossBarPresentation(1.0f, "Open Ways: free until dawn", BossEvent.BossBarColor.YELLOW)
        val charge = chargeFraction.coerceIn(0.0, 1.0)
        if (charge >= ObeliskConstants.BOSS_BAR_SHOW_THRESHOLD) return null
        return FontBossBarPresentation(
            progress = charge.toFloat(),
            title = "Font Charge: ${(charge * 100.0).toInt()}%",
            color = when {
                charge > ObeliskConstants.BOSS_BAR_GREEN_THRESHOLD -> BossEvent.BossBarColor.GREEN
                charge > ObeliskConstants.BOSS_BAR_YELLOW_THRESHOLD -> BossEvent.BossBarColor.YELLOW
                else -> BossEvent.BossBarColor.RED
            }
        )
    }
}

internal data class FontBossBarPresentation(
    val progress: Float,
    val title: String,
    val color: BossEvent.BossBarColor
)
