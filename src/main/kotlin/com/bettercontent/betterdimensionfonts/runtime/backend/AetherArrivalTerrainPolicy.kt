package com.bettercontent.betterdimensionfonts.runtime.backend

/** Pure first-arrival policy. Counts native namespace blocks, never the arrival anchor. */
internal object AetherArrivalTerrainPolicy {
    const val MIN_NATIVE_BLOCKS = 1024L
    private const val CHUNKS = 9

    data class Chunk(val x: Int, val z: Int)
    data class Cell(val x: Int, val y: Int, val z: Int)

    /** Direct writes of the unchanged layout, with the seal/clearance overlap deduplicated. */
    fun overwrittenCells(
        floor: Cell, offsets: List<Cell>, clearance: Int, minY: Int, submerged: Boolean,
        isSolid: (Cell) -> Boolean,
    ): Set<Cell> {
        val cells = hashSetOf<Cell>()
        for (offset in offsets) {
            val at = Cell(floor.x + offset.x, floor.y + offset.y, floor.z + offset.z)
            cells.add(at)
            var support = at.copy(y = at.y - 1)
            while (support.y >= minY && !isSolid(support)) {
                cells.add(support)
                support = support.copy(y = support.y - 1)
            }
            if (!submerged) for (dy in 1..clearance) cells.add(at.copy(y = at.y + dy))
        }
        cells.add(floor.copy(y = floor.y + 1))
        return cells
    }

    fun requiresFirstArrivalCensus(dimension: String, hasArrival: Boolean): Boolean =
        dimension == "aether:the_aether" && !hasArrival

    fun offsetColumn(origin: Int, offset: Int): Int? =
        try { Math.addExact(origin, offset) } catch (_: ArithmeticException) { null }

    fun centeredChunks(blockX: Int, blockZ: Int): List<Chunk> {
        val x = blockX shr 4
        val z = blockZ shr 4
        return buildList {
            for (dx in -1..1) for (dz in -1..1) add(Chunk(x + dx, z + dz))
        }
    }

    /** Null propagates a missing/invalid census; overflow must never admit a site. */
    fun addPaletteCount(total: Long?, namespace: String, count: Long): Long? {
        if (total == null || total < 0 || count !in 0L..4096L) return null
        if (namespace != "aether") return total
        return try { Math.addExact(total, count) } catch (_: ArithmeticException) { null }
    }

    fun projectedNativeBlocks(chunks: List<Long?>, overwrittenNativeBlocks: Long): Long? {
        if (chunks.size != CHUNKS || overwrittenNativeBlocks < 0) return null
        var total = 0L
        for (count in chunks) {
            if (count == null || count < 0) return null
            total = try { Math.addExact(total, count) } catch (_: ArithmeticException) { return null }
        }
        if (overwrittenNativeBlocks > total) return null
        return total - overwrittenNativeBlocks
    }

    fun qualifies(chunks: List<Long?>, overwrittenNativeBlocks: Long): Boolean =
        projectedNativeBlocks(chunks, overwrittenNativeBlocks)?.let { it >= MIN_NATIVE_BLOCKS } ?: false
}
