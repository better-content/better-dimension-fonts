package com.bettercontent.betterdimensionfonts.runtime

import com.bettercontent.betterdimensionfonts.content.ObeliskBlock
import com.bettercontent.betterdimensionfonts.content.ObeliskBlockEntity
import com.bettercontent.betterdimensionfonts.data.ObeliskDataManager
import com.bettercontent.betterdimensionfonts.registry.ModBlocks
import com.bettercontent.betterdimensionfonts.trade.FontLocationSavedData
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraftforge.fml.ModList
import java.util.Optional

/** No provider types escape the nested bridge: absent-mod class loading stays inert. */
object CelestialIntegration {
    data class OpenWays(val occurrenceId: Long, val remainingTicks: Long)

    fun register() {
        if (ModList.get().isLoaded("better_celestial_events")) Bridge.register()
    }

    fun openWays(server: MinecraftServer): OpenWays? =
        if (ModList.get().isLoaded("better_celestial_events")) Bridge.server(server) else null

    fun isOpenWays(level: Level?): Boolean = when {
        level == null -> false
        !ModList.get().isLoaded("better_celestial_events") -> false
        level.isClientSide -> Bridge.client()
        else -> level.server?.let { openWays(it) != null } ?: false
    }

    private object Bridge {
        fun server(server: MinecraftServer): OpenWays? {
            val snapshot = com.bettercontent.celestial.api.CelestialEventsApi.snapshot(server)
            return if (snapshot.active() && snapshot.event() == com.bettercontent.celestial.api.CelestialEvent.OPEN_WAYS)
                OpenWays(snapshot.occurrenceId(), snapshot.remainingNightTicks()) else null
        }

        fun client(): Boolean {
            val snapshot = com.bettercontent.celestial.api.CelestialEventsApi.clientSnapshot()
            return snapshot.active() && snapshot.event() == com.bettercontent.celestial.api.CelestialEvent.OPEN_WAYS
        }

        fun register() {
            com.bettercontent.celestial.api.CelestialEventsApi.registerFontLocator(
                object : com.bettercontent.celestial.api.FontLocator {
                    override fun nearest(level: ServerLevel, origin: BlockPos, radius: Int): Optional<com.bettercontent.celestial.api.FontTarget> {
                        if (radius < 0) return Optional.empty()
                        val target = FontLocationSavedData.get(level.server).snapshot().asSequence()
                            .filter { it.level == level.dimension() && it.pos.distSqr(origin) <= radius.toDouble() * radius }
                            .filter { valid(level, it.pos, it.definitionId) }
                            .minWithOrNull(compareBy<com.bettercontent.betterdimensionfonts.trade.FontLocation> { it.pos.distSqr(origin) }
                                .thenBy { it.definitionId }.thenBy { it.pos.asLong() }) ?: return Optional.empty()
                        return Optional.of(com.bettercontent.celestial.api.FontTarget(target.pos, target.definitionId))
                    }

                    override fun isValid(level: ServerLevel, target: com.bettercontent.celestial.api.FontTarget): Boolean =
                        valid(level, target.position(), target.definitionId())
                }
            )
        }

        private fun valid(level: ServerLevel, pos: BlockPos, definitionId: String): Boolean {
            // getChunkNow is a read-only loaded check, unlike getChunk/getBlockEntity on absent chunks.
            if (ObeliskDataManager.getObelisk(definitionId)?.enabled != true) return false
            if (!FontLocationSavedData.get(level.server).contains(level.dimension(), pos, definitionId)) return false
            val chunk = level.chunkSource.getChunkNow(pos.x shr 4, pos.z shr 4) ?: return true
            val font = chunk.blockEntities[pos] as? ObeliskBlockEntity ?: return false
            return !font.isRemoved && font.isNaturallyGenerated && font.definitionId == definitionId &&
                font.blockState.`is`(ModBlocks.OBELISK.get()) && font.blockState.getValue(ObeliskBlock.BOUND) &&
                ObeliskDataManager.getObelisk(definitionId)?.enabled == true
        }
    }
}
