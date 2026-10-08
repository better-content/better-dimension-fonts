package com.bettercontent.betterdimensionfonts.runtime.backend

import com.mojang.logging.LogUtils
import com.bettercontent.betterdimensionfonts.data.CanonicalTargetResolver
import com.bettercontent.betterdimensionfonts.data.ObeliskDataManager
import com.bettercontent.betterdimensionfonts.data.ObeliskDefinition
import com.bettercontent.betterdimensionfonts.registry.ModBlocks
import com.bettercontent.betterdimensionfonts.runtime.player.FontTravelAuthorization
import com.bettercontent.betterdimensionfonts.runtime.player.FontCompanions
import com.bettercontent.betterdimensionfonts.runtime.run.RunRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

object CanonicalDimensionBackend : RunWorldBackend {
    private const val SPAWN_CLEARANCE = 3
    private const val MAX_SAVED_SITES = 256
    private val logger = LogUtils.getLogger()
    private val playerBindings = linkedMapOf<UUID, UUID>()
    private val configCache = linkedMapOf<String, BackendConfig>()

    override fun validateTemplate(server: MinecraftServer, templateId: String): String? {
        val target = CanonicalTargetResolver.targetLevelKey(templateId) ?: return "target dimension for '$templateId' is unknown"
        if (server.getLevel(target) == null) {
            return "target dimension ${target.location()} is not loaded"
        }
        return null
    }

    override fun requestPreparedSite(
        server: MinecraftServer,
        templateId: String,
        originLevelKey: ResourceKey<Level>?,
        originObeliskPos: BlockPos?
    ): PreparedSiteResult {
        validateTemplate(server, templateId)?.let { return PreparedSiteResult.Rejected(it) }
        val targetKey = CanonicalTargetResolver.targetLevelKey(templateId)
            ?: return PreparedSiteResult.Rejected("target dimension for '$templateId' is unknown")
        val level = server.getLevel(targetKey)
            ?: return PreparedSiteResult.Rejected("target dimension ${targetKey.location()} is not loaded")
        val data = RunSiteSavedData.get(server)
        val config = configFor(templateId)
        val mapped = mapOriginToTarget(config, templateId, originObeliskPos ?: BlockPos.ZERO)
        val provisionalCenter = BlockPos(mapped.x, emergencySpawnY(level), mapped.z)
        val now = gameTime(server)
        val existing = reusableSite(data, templateId, originLevelKey, originObeliskPos)
        if (existing == null) {
            data.pruneInactive(MAX_SAVED_SITES - 1)
        }
        val record = existing ?: RunSiteRecord(
            siteId = UUID.randomUUID(),
            templateId = templateId,
            backendLevelKey = targetKey,
            siteCenter = provisionalCenter,
            siteBounds = boundsFor(provisionalCenter, level, config),
            siteIndex = 0L,
            originLevelKey = originLevelKey,
            originObeliskPos = originObeliskPos,
            state = SiteState.PREPARED,
            createdGameTime = now,
            updatedGameTime = now
        )

        record.originLevelKey = originLevelKey
        record.originObeliskPos = originObeliskPos
        record.runId = null
        record.ownerId = null
        record.state = SiteState.PREPARED
        record.updatedGameTime = now
        if (record.spawnPos == null) {
            record.siteCenter = provisionalCenter
            record.siteBounds = boundsFor(provisionalCenter, level, config)
        }

        if (existing == null) {
            data.upsert(record)
        } else {
            markSiteDirty(server, immediate = true)
        }
        return PreparedSiteResult.Accepted(record.preparedHandle())
    }

    override fun pollPreparedSite(server: MinecraftServer, handle: PreparedSiteHandle): PreparedSiteStatus {
        val record = site(server, handle.siteId) ?: return PreparedSiteStatus.Failed("site record is missing")
        return PreparedSiteStatus.Ready(record.spawnPos ?: BlockPos.ZERO)
    }

    override fun activateRun(server: MinecraftServer, handle: PreparedSiteHandle, runId: UUID, ownerId: UUID?): ActiveSiteResult {
        val record = site(server, handle.siteId) ?: return ActiveSiteResult.Rejected("site record is missing")
        record.state = SiteState.ACTIVE
        record.runId = runId
        record.ownerId = ownerId
        record.updatedGameTime = gameTime(server)
        markSiteDirty(server, immediate = true)
        logger.info(
            "Activated canonical font run site={} run={} target={} center={} origin={} {}",
            record.siteId,
            runId,
            record.backendLevelKey.location(),
            record.siteCenter,
            record.originLevelKey?.location(),
            record.originObeliskPos
        )
        return ActiveSiteResult.Accepted(record.activeHandle() ?: return ActiveSiteResult.Rejected("site did not activate"), record.spawnPos ?: BlockPos.ZERO)
    }

    override fun enterPlayer(player: ServerPlayer, handle: ActiveSiteHandle): EnterRunResult {
        val record = site(player.server, handle.siteId) ?: return EnterRunResult.Rejected("site record is missing")
        val level = player.server.getLevel(record.backendLevelKey)
            ?: return EnterRunResult.Rejected("target dimension ${record.backendLevelKey.location()} is not loaded")
        val spawn = resolveArrival(level, record, configFor(record.templateId))
            ?: return EnterRunResult.Rejected("no safe first Aether arrival retains sufficient native terrain")
        val landing = normalizedArrivalTeleportPos(level, spawn)
        record.updatedGameTime = gameTime(player.server)
        FontTravelAuthorization.authorize(player, level.dimension()) {
            player.teleportTo(level, landing.x + 0.5, landing.y.toDouble(), landing.z + 0.5, player.yRot, player.xRot)
        }
        playEntrySounds(level, spawn)
        playerBindings[player.uuid] = record.siteId
        return EnterRunResult.Entered
    }

    override fun returnPlayer(player: ServerPlayer): ReturnRunResult {
        val siteId = playerBindings[player.uuid]
            ?: RunRegistry.getRun(player.uuid)?.let { RunRegistry.get(it.runId)?.instanceId }
            ?: siteForPlayer(player)?.siteId
            ?: return ReturnRunResult.NotBound
        val record = site(player.server, siteId) ?: return ReturnRunResult.Rejected("site record is missing")
        val originLevelKey = record.originLevelKey ?: return ReturnRunResult.Rejected("origin level is missing")
        val originPos = record.originObeliskPos ?: return ReturnRunResult.Rejected("origin anchor is missing")
        val originLevel = player.server.getLevel(originLevelKey)
            ?: return ReturnRunResult.Rejected("origin level ${originLevelKey.location()} is not loaded")
        val x = originPos.x + 0.5
        val y = originPos.y + 1.0
        val z = originPos.z + 0.5
        player.fallDistance = 0.0f
        player.setDeltaMovement(Vec3.ZERO)
        FontTravelAuthorization.authorize(player, originLevel.dimension()) {
            player.teleportTo(originLevel, x, y, z, player.yRot, player.xRot)
        }
        player.moveTo(x, y, z, player.yRot, player.xRot)
        player.connection.resetPosition()
        player.fallDistance = 0.0f
        playReturnSounds(originLevel, BlockPos.containing(x, y, z))
        playerBindings.remove(player.uuid)
        return ReturnRunResult.Returned
    }

    override fun destroyRun(server: MinecraftServer, handle: ActiveSiteHandle, reason: String) {
        val record = site(server, handle.siteId) ?: return
        if (!AetherArrivalTerrainPolicy.requiresFirstArrivalCensus(
                record.backendLevelKey.location().toString(), record.spawnPos != null)) {
            server.getLevel(record.backendLevelKey)?.let { level ->
                despawnRunMobs(level, record)
            }
        }
        playerBindings.entries.removeIf { (_, siteId) -> siteId == record.siteId }
        record.state = SiteState.PREPARED
        record.runId = null
        record.ownerId = null
        record.updatedGameTime = gameTime(server)
        markSiteDirty(server, immediate = true)
        logger.info(
            "Closed canonical font run site={} target={} center={} reason={}",
            record.siteId,
            record.backendLevelKey.location(),
            record.siteCenter,
            reason
        )
    }

    override fun tick(server: MinecraftServer) = Unit

    override fun clearPlayer(playerId: UUID) {
        playerBindings.remove(playerId)
    }

    override fun isPlayerInRun(player: ServerPlayer, handle: ActiveSiteHandle): Boolean {
        return player.serverLevel().dimension() == handle.backendLevelKey
    }

    override fun describeProgress(server: MinecraftServer, handle: PreparedSiteHandle): String {
        val record = site(server, handle.siteId) ?: return "missing"
        return "ready target=${record.backendLevelKey.location()} arrival=${record.spawnPos ?: record.siteCenter}"
    }

    override fun findActiveHandle(server: MinecraftServer, siteId: UUID): ActiveSiteHandle? = site(server, siteId)?.activeHandle()

    internal fun isChunkLoadedForTests(level: ServerLevel, blockX: Int, blockZ: Int): Boolean {
        return level.chunkSource.getChunkNow(blockX shr 4, blockZ shr 4) != null
    }

    private fun reusableSite(
        data: RunSiteSavedData,
        templateId: String,
        originLevelKey: ResourceKey<Level>?,
        originObeliskPos: BlockPos?
    ): RunSiteRecord? {
        return data.find {
            it.templateId == templateId &&
                it.originLevelKey == originLevelKey &&
                it.originObeliskPos == originObeliskPos &&
                it.state != SiteState.ACTIVE
        }
    }

    private fun resolveArrival(level: ServerLevel, record: RunSiteRecord, config: BackendConfig): BlockPos? {
        val resolvedFloor = if (record.spawnPos != null) {
            record.spawnPos!!.below(2)
        } else {
            val desired = record.siteCenter
            if (AetherArrivalTerrainPolicy.requiresFirstArrivalCensus(
                    record.backendLevelKey.location().toString(), false)) {
                findAetherFloor(level, desired.x, desired.z, config.spawnSearchRadius) ?: return null
            } else {
                findSafeFloor(level, desired.x, desired.z, config.spawnSearchRadius)
                    ?: BlockPos(desired.x, emergencySpawnY(level) - 1, desired.z)
            }
        }
        ensureArrivalAnchor(level, resolvedFloor)
        val spawn = resolvedFloor.above(2).immutable()
        record.spawnPos = spawn
        record.siteCenter = spawn
        record.siteBounds = boundsFor(spawn, level, config)
        markSiteDirty(level.server, immediate = true)
        return spawn
    }

    internal fun ensureArrivalAnchor(level: ServerLevel, floor: BlockPos) {
        val submerged = level.getFluidState(floor.above()).`is`(net.minecraft.tags.FluidTags.WATER)
        ArrivalSiteLayout.floorOffsets().forEach { offset ->
            val floorPos = floor.offset(offset)
            val corner = kotlin.math.abs(offset.x) == ArrivalSiteLayout.FLOOR_RADIUS &&
                kotlin.math.abs(offset.z) == ArrivalSiteLayout.FLOOR_RADIUS
            level.setBlock(floorPos,
                if (submerged && corner) Blocks.SOUL_SAND.defaultBlockState() else Blocks.OXIDIZED_COPPER.defaultBlockState(), 3)
            var supportPos = floorPos.below()
            while (supportPos.y >= level.minBuildHeight && !level.getBlockState(supportPos).isSolid) {
                level.setBlock(supportPos, Blocks.OXIDIZED_COPPER.defaultBlockState(), 3)
                supportPos = supportPos.below()
            }
            if (!submerged) {
                for (dy in 1..ArrivalSiteLayout.CLEARANCE_HEIGHT) {
                    level.setBlock(floorPos.above(dy), Blocks.AIR.defaultBlockState(), 3)
                }
            }
        }
        level.setBlock(floor.above(), ModBlocks.RETURN_FONT.get().defaultBlockState()
            .setValue(com.bettercontent.betterdimensionfonts.content.ObeliskBlock.WATERLOGGED, submerged), 3)
    }

    private fun normalizedArrivalTeleportPos(level: ServerLevel, spawn: BlockPos): BlockPos {
        val spawnState = level.getBlockState(spawn)
        return when {
            spawnState.isAir -> spawn
            level.getBlockState(spawn.above()).isAir -> spawn.above()
            else -> spawn
        }
    }

    private fun blockOrNull(id: String): net.minecraft.world.level.block.Block? {
        val location = ResourceLocation.tryParse(id) ?: return null
        val block = BuiltInRegistries.BLOCK.get(location)
        return block.takeUnless { it == Blocks.AIR }
    }

    private fun playEntrySounds(level: ServerLevel, at: BlockPos) {
        level.playSound(null, at, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.55f, 1.25f)
        level.playSound(null, at, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.45f, 1.65f)
    }

    private fun playReturnSounds(level: ServerLevel, at: BlockPos) {
        level.playSound(null, at, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.65f, 0.85f)
        level.playSound(null, at, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.45f, 1.3f)
    }

    private fun mapOriginToTarget(config: BackendConfig, templateId: String, origin: BlockPos): BlockPos {
        val scale = config.coordinateScale ?: CanonicalTargetResolver.coordinateScale(templateId)
        return BlockPos((origin.x * scale).roundToInt(), origin.y, (origin.z * scale).roundToInt())
    }

    private fun findSafeFloor(level: ServerLevel, x: Int, z: Int, searchRadius: Int): BlockPos? {
        val radius = searchRadius.coerceAtLeast(0)
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val floor = findSafeFloor(level, x + dx, z + dz)
                if (floor != null) return floor
            }
        }
        return null
    }

    /** Search-local census only; no contents cache or changes to generation/anchor geometry. */
    private fun findAetherFloor(level: ServerLevel, x: Int, z: Int, searchRadius: Int): BlockPos? {
        val census = mutableMapOf<AetherArrivalTerrainPolicy.Chunk, Long?>()
        val radius = searchRadius.coerceAtLeast(0)
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val columnX = AetherArrivalTerrainPolicy.offsetColumn(x, dx) ?: continue
                val columnZ = AetherArrivalTerrainPolicy.offsetColumn(z, dz) ?: continue
                val chunks = AetherArrivalTerrainPolicy.centeredChunks(columnX, columnZ)
                val counts = chunks.map { key ->
                    if (census.containsKey(key)) census[key]
                    else countNativeAether(level, key).also { census[key] = it }
                }
                // Even zero anchor loss cannot admit this chunk footprint. Avoid scanning its columns.
                if (!AetherArrivalTerrainPolicy.qualifies(counts, 0)) continue
                val floor = findSafeFloor(level, columnX, columnZ) { candidate ->
                    val loss = overwrittenNativeAether(level, candidate)
                    if (!AetherArrivalTerrainPolicy.qualifies(counts, loss)) false
                    else {
                        // Neighbor generation during the bounded search can alter a memoized chunk.
                        // Recount the exact nine before acceptance, still BEFORE any anchor write.
                        val fresh = chunks.map { key -> countNativeAether(level, key).also { census[key] = it } }
                        val freshLoss = overwrittenNativeAether(level, candidate)
                        AetherArrivalTerrainPolicy.qualifies(fresh, freshLoss)
                    }
                }
                if (floor != null) return floor
            }
        }
        return null
    }

    private fun countNativeAether(level: ServerLevel, key: AetherArrivalTerrainPolicy.Chunk): Long? {
        val chunk = level.getChunk(key.x, key.z)
        var count: Long? = 0L
        for (section in chunk.sections) {
            section.states.count { state, amount ->
                count = AetherArrivalTerrainPolicy.addPaletteCount(
                    count, BuiltInRegistries.BLOCK.getKey(state.block).namespace, amount.toLong())
            }
        }
        return count
    }

    /** Exact distinct cells written by ensureArrivalAnchor; no place/undo trial anchors. */
    private fun overwrittenNativeAether(level: ServerLevel, floor: BlockPos): Long {
        val submerged = level.getFluidState(floor.above()).`is`(net.minecraft.tags.FluidTags.WATER)
        val overwritten = AetherArrivalTerrainPolicy.overwrittenCells(
            AetherArrivalTerrainPolicy.Cell(floor.x, floor.y, floor.z),
            ArrivalSiteLayout.floorOffsets().map { AetherArrivalTerrainPolicy.Cell(it.x, it.y, it.z) },
            ArrivalSiteLayout.CLEARANCE_HEIGHT, level.minBuildHeight, submerged,
        ) { cell -> level.getBlockState(BlockPos(cell.x, cell.y, cell.z)).isSolid }
        return overwritten.count { cell ->
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos(cell.x, cell.y, cell.z)).block).namespace == "aether"
        }.toLong()
    }

    internal fun findSafeFloor(level: ServerLevel, x: Int, z: Int): BlockPos? =
        findSafeFloor(level, x, z) { true }

    private fun findSafeFloor(level: ServerLevel, x: Int, z: Int, accepts: (BlockPos) -> Boolean): BlockPos? {
        level.getChunk(BlockPos(x, level.minBuildHeight, z))
        val highestFeetY = level.maxBuildHeight - SPAWN_CLEARANCE - 1
        for (y in highestFeetY downTo level.minBuildHeight + 1) {
            val floor = BlockPos(x, y - 1, z)
            val feet = BlockPos(x, y, z)
            val head = BlockPos(x, y + 1, z)
            val floorState = level.getBlockState(floor)
            val floorFluid = level.getFluidState(floor)
            if (
                floorState.isSolid &&
                !floorState.`is`(Blocks.BEDROCK) &&
                floorFluid.isEmpty &&
                level.getBlockState(feet).isAir &&
                level.getBlockState(head).isAir &&
                level.getFluidState(feet).isEmpty &&
                level.getFluidState(head).isEmpty
            ) {
                if (accepts(floor)) return floor
            }
            if (floorState.isSolid && !floorState.`is`(Blocks.BEDROCK) &&
                floorFluid.isEmpty && level.getFluidState(feet).`is`(net.minecraft.tags.FluidTags.WATER) &&
                level.getFluidState(head).`is`(net.minecraft.tags.FluidTags.WATER) &&
                bubbleShaftsReachSurface(level, floor)) {
                if (accepts(floor)) return floor
            }
        }
        return null
    }

    private fun bubbleShaftsReachSurface(level: ServerLevel, floor: BlockPos): Boolean =
        listOf(-ArrivalSiteLayout.FLOOR_RADIUS, ArrivalSiteLayout.FLOOR_RADIUS).all { dx ->
            listOf(-ArrivalSiteLayout.FLOOR_RADIUS, ArrivalSiteLayout.FLOOR_RADIUS).all shaft@{ dz ->
                var sawWater = false
                for (y in floor.y + 1 until level.maxBuildHeight) {
                    val pos = BlockPos(floor.x + dx, y, floor.z + dz)
                    val state = level.getBlockState(pos)
                    if (state.`is`(Blocks.WATER) && level.getFluidState(pos).isSource) {
                        sawWater = true
                    } else {
                        if (!state.isAir) return@shaft false
                        break
                    }
                }
                sawWater
            }
        }

    private fun emergencySpawnY(level: ServerLevel): Int {
        return max(level.minBuildHeight + 80, 72).coerceAtMost(level.maxBuildHeight - 4)
    }

    private fun despawnRunMobs(level: ServerLevel, record: RunSiteRecord) {
        level.getEntitiesOfClass(
            Mob::class.java,
            AABB(
                record.siteBounds.minX.toDouble(),
                record.siteBounds.minY.toDouble(),
                record.siteBounds.minZ.toDouble(),
                record.siteBounds.maxX.toDouble(),
                record.siteBounds.maxY.toDouble(),
                record.siteBounds.maxZ.toDouble()
            )
        ).filterNot(FontCompanions::shouldPreserveOnRunClose).forEach(Mob::discard)
    }

    private fun boundsFor(center: BlockPos, level: ServerLevel, config: BackendConfig): SiteBounds {
        return SiteBounds(
            minX = center.x - config.runRadius,
            minY = level.minBuildHeight,
            minZ = center.z - config.runRadius,
            maxX = center.x + config.runRadius,
            maxY = level.maxBuildHeight - 1,
            maxZ = center.z + config.runRadius
        )
    }

    private fun site(server: MinecraftServer, siteId: UUID): RunSiteRecord? {
        return RunSiteSavedData.get(server).get(siteId)
    }

    private fun siteForPlayer(player: ServerPlayer): RunSiteRecord? {
        return RunSiteSavedData.get(player.server).find {
            it.state == SiteState.ACTIVE &&
                it.backendLevelKey == player.serverLevel().dimension() &&
                it.siteBounds.contains(player.blockPosition())
        }
    }

    private fun markSiteDirty(server: MinecraftServer, @Suppress("UNUSED_PARAMETER") immediate: Boolean = false) {
        RunSiteSavedData.get(server).setDirty()
    }

    private fun gameTime(server: MinecraftServer): Long = server.overworld().gameTime

    private fun configFor(templateId: String): BackendConfig {
        return configCache.getOrPut(templateId) {
            val definition = ObeliskDataManager.getObelisk(templateId)
                ?: ObeliskDataManager.allDimensionDrinks().firstOrNull {
                    it.instanceTemplateId == templateId || it.targetDimension == templateId
                }
            BackendConfig.from(definition, templateId)
        }
    }

    private data class BackendConfig(
        val coordinateScale: Double?,
        val runRadius: Int,
        val spawnSearchRadius: Int
    ) {
        companion object {
            fun from(definition: ObeliskDefinition?, templateId: String): BackendConfig {
                return BackendConfig(
                    coordinateScale = definition?.coordinateScale ?: CanonicalTargetResolver.coordinateScale(templateId),
                    runRadius = definition?.runRadius ?: 96,
                    spawnSearchRadius = definition?.spawnSearchRadius ?: 16
                )
            }
        }
    }
}
