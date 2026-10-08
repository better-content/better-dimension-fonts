package com.bettercontent.betterdimensionfonts.runtime.backend

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AetherArrivalTerrainPolicyTest {
    private val policy = AetherArrivalTerrainPolicy
    private fun nine(total: Long): List<Long?> = listOf(total) + List(8) { 0L }
    private fun palette(vararg entries: Pair<String, Long>): Long? =
        entries.fold(0L as Long?) { total, (namespace, count) -> policy.addPaletteCount(total, namespace, count) }
    private val offsets = (-2..2).flatMap { x -> (-2..2).map { z -> AetherArrivalTerrainPolicy.Cell(x, 0, z) } }
    private val floor = AetherArrivalTerrainPolicy.Cell(-1, 10, -1)
    private val source by lazy { Files.readString(Path.of(
        "src/main/kotlin/com/bettercontent/betterdimensionfonts/runtime/backend/CanonicalDimensionBackend.kt")) }
    private fun body(from: String, to: String): String = source.substringAfter(from).substringBefore(to)

    @Test fun capturedSparseSiteDoesNotBecomeNative1024() {
        // Actual captured census: Aether963 + DynamicTreesAether40 = non-synthetic1003.
        val native = palette("aether" to 963L, "dtaether" to 40L, "minecraft" to 619L,
            "better_dimension_fonts" to 1L)
        assertEquals(963L, native)
        assertFalse(policy.qualifies(nine(native!!), 0))
        assertFalse(policy.qualifies(nine(1003), 0))
    }

    @Test fun exact1024BoundaryQualifiesWithoutLoss() {
        assertTrue(policy.qualifies(nine(1024), 0))
        assertFalse(policy.qualifies(nine(1023), 0))
    }

    @Test fun exactBoundaryIsAfterThePlannedOverwrite() {
        assertEquals(1024L, policy.projectedNativeBlocks(nine(1025), 1))
        assertTrue(policy.qualifies(nine(1025), 1))
        assertFalse(policy.qualifies(nine(1025), 2))
    }

    @Test fun syntheticAndOtherNamespacesNeverSupplyNativeCapacity() {
        assertEquals(0L, palette("minecraft" to 4096L, "better_dimension_fonts" to 4096L,
            "dtaether" to 4096L, "aether_fake" to 4096L))
        assertFalse(policy.qualifies(nine(0), 0))
    }

    @Test fun Native1024AlsoMeetsUnchangedNativeFamily256Floor() {
        assertTrue(policy.MIN_NATIVE_BLOCKS >= 256L)
        assertTrue(policy.qualifies(nine(palette("aether" to 1024L)!!), 0))
    }

    @Test fun negativeBlockCoordinatesUseFloorChunkCentering() {
        val chunks = policy.centeredChunks(-1, -17)
        assertEquals(9, chunks.size)
        assertEquals(9, chunks.toSet().size)
        assertEquals(setOf(-2, -1, 0), chunks.map { it.x }.toSet())
        assertEquals(setOf(-3, -2, -1), chunks.map { it.z }.toSet())
    }

    @Test fun chunkBoundariesAndExtremeCoordinatesRemainExactlyNine() {
        assertEquals(setOf(-1, 0, 1), policy.centeredChunks(15, 0).map { it.x }.toSet())
        assertEquals(setOf(0, 1, 2), policy.centeredChunks(16, 0).map { it.x }.toSet())
        assertEquals(9, policy.centeredChunks(Int.MIN_VALUE, Int.MAX_VALUE).toSet().size)
    }

    @Test fun searchCoordinatesNeverWrapToRescueCoordinates() {
        assertEquals(-94, policy.offsetColumn(2, -96))
        assertNull(policy.offsetColumn(Int.MAX_VALUE, 1))
        assertNull(policy.offsetColumn(Int.MIN_VALUE, -1))
    }

    @Test fun missingOrWrongSizedFootprintRejects() {
        assertFalse(policy.qualifies(List(8) { 1024L }, 0))
        assertFalse(policy.qualifies(List(10) { 1024L }, 0))
        assertFalse(policy.qualifies(listOf(null) + List(8) { 1024L }, 0))
    }

    @Test fun negativeCountsAndInvalidRemovalReject() {
        assertFalse(policy.qualifies(nine(-1), 0))
        assertFalse(policy.qualifies(nine(1024), -1))
        assertFalse(policy.qualifies(nine(1024), 1025))
        assertNull(policy.addPaletteCount(0L, "aether", -1))
        assertNull(policy.addPaletteCount(0L, "minecraft", -1))
        assertNull(policy.addPaletteCount(null, "aether", 1000))
        assertNull(policy.addPaletteCount(0L, "aether", 4097))
        assertEquals(4096L, policy.addPaletteCount(0L, "aether", 4096))
    }

    @Test fun paletteAndNineChunkOverflowFailClosed() {
        assertNull(policy.addPaletteCount(Long.MAX_VALUE, "aether", 1))
        assertEquals(Long.MAX_VALUE, policy.addPaletteCount(Long.MAX_VALUE, "minecraft", 1))
        assertFalse(policy.qualifies(listOf(Long.MAX_VALUE, 1L) + List(7) { 0L }, 0))
    }

    @Test fun dryLayoutHas25Floors75ClearanceAndNoDuplicateSeal() {
        val cells = policy.overwrittenCells(floor, offsets, 3, 0, false) { true }
        assertEquals(100, cells.size)
        assertTrue(cells.contains(floor.copy(y = 11)))
        assertEquals(25, cells.count { it.y == 10 })
        assertEquals(75, cells.count { it.y > 10 })
        assertFalse(cells.any { it.y < 10 })
    }

    @Test fun supportsStopBeforeFirstSolidBlockAndRespectMinHeight() {
        val cells = policy.overwrittenCells(floor, offsets, 3, 8, false) { it.y < 8 }
        assertEquals(150, cells.size)
        assertEquals(50, cells.count { it.y < 10 })
        assertFalse(cells.any { it.y < 8 })
        val stopped = policy.overwrittenCells(floor, offsets, 3, 0, false) { it.y <= 8 }
        assertEquals(125, stopped.size)
        assertFalse(stopped.any { it.y <= 8 })
    }

    @Test fun submergedLayoutKeepsExistingClearanceAndCountsSealOnce() {
        val cells = policy.overwrittenCells(floor, offsets, 3, 0, true) { true }
        assertEquals(26, cells.size)
        assertEquals(setOf(floor.copy(y = 11)), cells.filter { it.y > 10 }.toSet())
    }

    @Test fun exactFootprintLossIsSubtractedNotCountedAsAnAnchorBonus() {
        val cells = policy.overwrittenCells(floor, offsets, 3, 0, false) { true }
        val loss = cells.count { it.y == 10 }.toLong()
        assertEquals(25L, loss)
        assertTrue(policy.qualifies(nine(1049), loss))
        assertFalse(policy.qualifies(nine(1048), loss))
    }

    @Test fun namespaceAndArrivalScopePreserveAllFourCases() {
        assertTrue(policy.requiresFirstArrivalCensus("aether:the_aether", false))
        assertFalse(policy.requiresFirstArrivalCensus("aether:the_aether", true))
        assertFalse(policy.requiresFirstArrivalCensus("minecraft:the_nether", false))
        assertFalse(policy.requiresFirstArrivalCensus("minecraft:the_nether", true))
    }

    @Test fun rejectedArrivalReturnsBeforeTimeTeleportBindingOrAnchorWrites() {
        val enter = body("override fun enterPlayer(", "override fun returnPlayer(")
        val reject = enter.indexOf("?: return EnterRunResult.Rejected(\"no safe first Aether")
        assertTrue(reject >= 0)
        for (operation in listOf("normalizedArrivalTeleportPos", "record.updatedGameTime =",
            "player.teleportTo(", "playerBindings[player.uuid] =")) assertTrue(enter.indexOf(operation) > reject)
        assertFalse(enter.contains("ensureArrivalAnchor("))
    }

    @Test fun cachedArrivalAndNonAetherFallbackPrecedeUnchangedAnchorMutation() {
        val resolve = body("private fun resolveArrival(", "internal fun ensureArrivalAnchor(")
        assertTrue(resolve.indexOf("record.spawnPos != null") < resolve.indexOf("findAetherFloor("))
        assertTrue(resolve.contains("record.spawnPos!!.below(2)"))
        val aether = resolve.substringAfter("if (AetherArrivalTerrainPolicy.requiresFirstArrivalCensus(")
            .substringBefore("} else {")
        assertTrue(aether.contains("findAetherFloor(level, desired.x, desired.z, config.spawnSearchRadius) ?: return null"))
        assertFalse(aether.contains("emergencySpawnY"))
        assertTrue(resolve.substringAfter(aether).contains("emergencySpawnY(level) - 1"))
        assertTrue(resolve.indexOf("ensureArrivalAnchor(level, resolvedFloor)") > resolve.indexOf("?: return null"))
    }

    @Test fun exactRuntimeLayoutProjectionIsReadOnlyAndUsesNamespacePalettes() {
        val projection = body("private fun overwrittenNativeAether(", "internal fun findSafeFloor(")
        assertTrue(projection.contains("ArrivalSiteLayout.floorOffsets()"))
        assertTrue(projection.contains("ArrivalSiteLayout.CLEARANCE_HEIGHT"))
        assertTrue(projection.contains("level.minBuildHeight, submerged"))
        assertTrue(projection.contains(".isSolid"))
        assertTrue(projection.contains(".namespace == \"aether\""))
        assertFalse(projection.contains("setBlock"))
        val count = body("private fun countNativeAether(", "private fun overwrittenNativeAether(")
        assertTrue(count.contains("level.getChunk(key.x, key.z)"))
        assertTrue(count.contains("for (section in chunk.sections)"))
        assertTrue(count.contains("section.states.count"))
        assertTrue(count.contains("amount.toLong()"))
        val layout = Files.readString(Path.of(
            "src/main/kotlin/com/bettercontent/betterdimensionfonts/runtime/backend/ArrivalSiteLayout.kt"))
        assertTrue(layout.contains("const val FLOOR_RADIUS = 2"))
        assertTrue(layout.contains("const val CLEARANCE_HEIGHT = 3"))
    }

    @Test fun boundedMemoSearchFreshlyRecountsAndReprojectsBeforeAcceptance() {
        val search = body("private fun findAetherFloor(", "private fun countNativeAether(")
        assertTrue(search.contains("val census = mutableMapOf"))
        assertTrue(search.contains("searchRadius.coerceAtLeast(0)"))
        assertTrue(search.contains("for (dx in -radius..radius)"))
        assertTrue(search.contains("for (dz in -radius..radius)"))
        assertTrue(search.contains("centeredChunks(columnX, columnZ)"))
        assertTrue(search.contains("qualifies(counts, 0)"))
        assertTrue(search.contains("val fresh = chunks.map"))
        assertTrue(search.contains("val freshLoss = overwrittenNativeAether(level, candidate)"))
        assertTrue(search.contains("qualifies(fresh, freshLoss)"))
        assertFalse(search.contains("setBlock"))
        assertFalse(search.contains("ensureArrivalAnchor"))
    }

    @Test fun onlyUnarrivedAetherSkipsNativeMobCleanup() {
        val destroy = body("override fun destroyRun(", "override fun tick(")
        assertTrue(destroy.contains("if (!AetherArrivalTerrainPolicy.requiresFirstArrivalCensus("))
        assertTrue(destroy.contains("record.backendLevelKey.location().toString(), record.spawnPos != null"))
        assertTrue(destroy.indexOf("requiresFirstArrivalCensus") < destroy.indexOf("despawnRunMobs"))
    }
}
