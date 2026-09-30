package com.bettercontent.dimensiondrink.worldgen.structure

import com.bettercontent.dimensiondrink.content.ObeliskBlockEntity
import com.bettercontent.dimensiondrink.data.ObeliskDefinition
import com.bettercontent.dimensiondrink.registry.ModBlocks
import com.bettercontent.dimensiondrink.worldgen.preferredAltarSconceBlockIds
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.BoundingBox
import kotlin.math.abs
import kotlin.math.max

/**
 * Materializes a font site one vanilla structure slice at a time.
 *
 * Layout decisions depend only on the serialized site data and absolute block coordinates. Terrain
 * reads and writes are restricted to the supplied structure box, so callback order and C2ME's
 * callback chunk bookkeeping cannot change the result.
 */
object DimensionalFontSiteGenerator {
    const val LAYOUT_VERSION = 4
    const val SITE_RADIUS = 32
    const val COURT_RADIUS = 5
    const val ALTAR_RADIUS = 3
    private const val FONT_CLEARANCE = 3
    private const val UPDATE_FLAGS = Block.UPDATE_CLIENTS

    fun anchorForStartChunk(chunk: ChunkPos, random: net.minecraft.util.RandomSource): BlockPos {
        val span = 16 - COURT_RADIUS * 2
        val localX = COURT_RADIUS + random.nextInt(span)
        val localZ = COURT_RADIUS + random.nextInt(span)
        return BlockPos(chunk.minBlockX + localX, 0, chunk.minBlockZ + localZ)
    }

    fun place(
        level: WorldGenLevel,
        box: BoundingBox,
        center: BlockPos,
        siteSeed: Long,
        definition: ObeliskDefinition,
        maxCharge: Double
    ) {
        placeLocalDressing(level, box, center, siteSeed, definition)
        if (box.isInside(center.offset(0, 3, 0))) {
            placeCenter(level, box, center, siteSeed, definition, maxCharge)
        }
    }

    internal fun centerFitsStartChunk(center: BlockPos): Boolean {
        val localX = Math.floorMod(center.x, 16)
        val localZ = Math.floorMod(center.z, 16)
        return localX in COURT_RADIUS..(15 - COURT_RADIUS) &&
            localZ in COURT_RADIUS..(15 - COURT_RADIUS)
    }

    internal fun isRingColumn(dx: Int, dz: Int): Boolean {
        val squared = dx * dx + dz * dz
        return squared in 81..121
    }

    internal fun altarApproachStairPositions(center: BlockPos, direction: Direction): List<BlockPos> = listOf(
        center.relative(direction, 2).above(),
        center.relative(direction).above(2)
    )

    internal fun coordinateHash(seed: Long, x: Int, z: Int, salt: Long = 0L): Long {
        var value = seed xor salt
        value = value xor (x.toLong() * -7046029254386353131L)
        value = value xor (z.toLong() * -4658895280553007687L)
        value = value xor (value ushr 30)
        value *= -4658895280553007687L
        value = value xor (value ushr 27)
        value *= -7723592293110705685L
        return value xor (value ushr 31)
    }

    private fun placeCenter(
        level: WorldGenLevel,
        box: BoundingBox,
        center: BlockPos,
        siteSeed: Long,
        definition: ObeliskDefinition,
        maxCharge: Double
    ) {
        require(centerFitsStartChunk(center)) { "Dimensional font center must fit inside its start chunk" }

        for (dx in -COURT_RADIUS..COURT_RADIUS) {
            for (dz in -COURT_RADIUS..COURT_RADIUS) {
                val x = center.x + dx
                val z = center.z + dz
                val groundY = localGroundY(level, box, x, z) ?: continue
                val squared = dx * dx + dz * dz
                if (squared > COURT_RADIUS * COURT_RADIUS) continue
                if (squared > ALTAR_RADIUS * ALTAR_RADIUS) {
                    val court = BlockPos(x, groundY, z)
                    setBoxed(level, box, court, courtState(siteSeed, court))
                    continue
                }

                for (y in groundY..center.y) {
                    val foundation = BlockPos(x, y, z)
                    setBoxed(level, box, foundation, copperState(Blocks.CUT_COPPER, foundation, center))
                }
                setBoxed(level, box, BlockPos(x, center.y, z), copperState(Blocks.CUT_COPPER, BlockPos(x, center.y, z), center))
                if (squared <= 4) {
                    val middle = BlockPos(x, center.y + 1, z)
                    setBoxed(level, box, middle, copperState(Blocks.COPPER_BLOCK, middle, center))
                }
                if (squared <= 1) {
                    val upper = BlockPos(x, center.y + 2, z)
                    setBoxed(level, box, upper, copperState(Blocks.COPPER_BLOCK, upper, center))
                }
            }
        }

        val pedestal = center.above(2)
        setBoxed(level, box, pedestal, Blocks.OXIDIZED_COPPER.defaultBlockState())
        placeAltarApproachStairs(level, box, center)
        val fontPos = center.above(3)
        setBoxed(level, box, fontPos, ModBlocks.OBELISK.get().defaultBlockState()
            .setValue(com.bettercontent.dimensiondrink.content.ObeliskBlock.BOUND, true))
        for (dy in 1..FONT_CLEARANCE) {
            setBoxed(level, box, fontPos.above(dy), Blocks.AIR.defaultBlockState())
        }

        placeCenterDetails(level, box, center, siteSeed, definition)
        (level.getBlockEntity(fontPos) as? ObeliskBlockEntity)
            ?.initializeGeneratedFont(definition.id, maxCharge)
    }

    private fun placeAltarApproachStairs(level: WorldGenLevel, box: BoundingBox, center: BlockPos) {
        Direction.Plane.HORIZONTAL.forEach { direction ->
            altarApproachStairPositions(center, direction).forEach { pos ->
                setBoxed(level, box, pos, roofState(Blocks.CUT_COPPER_STAIRS, pos, direction.opposite))
            }
        }
    }

    private fun placeCenterDetails(
        level: WorldGenLevel,
        box: BoundingBox,
        center: BlockPos,
        siteSeed: Long,
        definition: ObeliskDefinition
    ) {
        val supportY = center.y + 4
        val strippedLog = strippedLogForBiome(level, center)
        if (strippedLog != null) {
            listOf(-2 to -2, -2 to 2, 2 to -2, 2 to 2).forEach { (dx, dz) ->
                for (y in center.y + 2..supportY) {
                    val support = BlockPos(center.x + dx, y, center.z + dz)
                    var state = strippedLog.defaultBlockState()
                    if (state.hasProperty(BlockStateProperties.AXIS)) {
                        state = state.setValue(BlockStateProperties.AXIS, Direction.Axis.Y)
                    }
                    setBoxed(level, box, support, state)
                }
            }
            placeAltarSideSconces(level, box, center, supportY)
            placeAltarCopperRoof(level, box, center, supportY + 1)
        }

        val potBase = center.offset(0, 1, 4)
        setBoxed(level, box, potBase, copperState(Blocks.CUT_COPPER, potBase, center))
        setBoxed(level, box, potBase.above(), Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState())

        val trophies = paletteBlocks(definition, PaletteKind.TROPHY, listOf(Blocks.LANTERN, Blocks.WHITE_CANDLE))
        val offsets = listOf(-4 to -4, 4 to -4, -4 to 4, 4 to 4)
        offsets.forEachIndexed { index, (dx, dz) ->
            val base = BlockPos(center.x + dx, center.y + 1, center.z + dz)
            setBoxed(level, box, base, Blocks.OXIDIZED_COPPER.defaultBlockState())
            val trophyPos = base.above()
            val trophy = trophies[Math.floorMod(coordinateHash(siteSeed, trophyPos.x, trophyPos.z).toInt() + index, trophies.size)]
            val state = preparedState(trophy)
            if (state.canSurvive(level, trophyPos)) setBoxed(level, box, trophyPos, state)
        }
    }

    private fun placeAltarCopperRoof(
        level: WorldGenLevel,
        box: BoundingBox,
        center: BlockPos,
        roofY: Int
    ) {
        for (dx in -2..2) {
            for (dz in -2..2) {
                if (max(abs(dx), abs(dz)) != 2) continue
                val pos = center.offset(dx, roofY - center.y, dz)
                val block = when {
                    abs(dx) == 2 && abs(dz) == 2 -> roofSlabBlock(pos)
                    else -> roofStairBlock(pos)
                }
                val facing = when {
                    dx < 0 -> Direction.WEST
                    dx > 0 -> Direction.EAST
                    dz < 0 -> Direction.NORTH
                    else -> Direction.SOUTH
                }
                setBoxed(level, box, pos, roofState(block, pos, facing))
            }
        }
    }

    private fun placeAltarSideSconces(
        level: WorldGenLevel,
        box: BoundingBox,
        center: BlockPos,
        supportTopY: Int
    ) {
        listOf(
            BlockPos(center.x - 2, supportTopY, center.z - 3) to Direction.NORTH,
            BlockPos(center.x - 3, supportTopY, center.z - 2) to Direction.WEST,
            BlockPos(center.x - 2, supportTopY, center.z + 3) to Direction.SOUTH,
            BlockPos(center.x - 3, supportTopY, center.z + 2) to Direction.WEST,
            BlockPos(center.x + 2, supportTopY, center.z - 3) to Direction.NORTH,
            BlockPos(center.x + 3, supportTopY, center.z - 2) to Direction.EAST,
            BlockPos(center.x + 2, supportTopY, center.z + 3) to Direction.SOUTH,
            BlockPos(center.x + 3, supportTopY, center.z + 2) to Direction.EAST
        ).forEach { (pos, facing) ->
            val key = preferredAltarSconceBlockIds()
                .asSequence()
                .mapNotNull(ResourceLocation::tryParse)
                .mapNotNull { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) }
                .firstOrNull { it != Blocks.AIR }
                ?: Blocks.LANTERN
            setBoxed(level, box, pos, sconceState(key, pos, facing))
        }
    }

    private fun sconceState(block: Block, pos: BlockPos, facing: Direction): BlockState {
        var state = block.defaultBlockState()
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
        } else if (state.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, facing)
        }
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            state = state.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
        }
        if (state.hasProperty(BlockStateProperties.LIT)) {
            state = state.setValue(BlockStateProperties.LIT, true)
        }
        return state
    }

    private fun roofState(block: Block, pos: BlockPos, facing: Direction): BlockState {
        var state = copperState(block, pos, null)
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
        } else if (state.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, facing)
        }
        if (state.hasProperty(BlockStateProperties.STAIRS_SHAPE)) {
            state = state.setValue(BlockStateProperties.STAIRS_SHAPE, net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT)
        }
        return state
    }

    private fun roofSlabBlock(pos: BlockPos): Block =
        optionalBlock("create", "create:oxidized_copper_shingle_slab")
            ?: Blocks.OXIDIZED_CUT_COPPER_SLAB

    private fun roofStairBlock(pos: BlockPos): Block =
        optionalBlock("create", "create:oxidized_copper_shingle_stairs")
            ?: Blocks.OXIDIZED_CUT_COPPER_STAIRS

    private fun optionalBlock(namespace: String, vararg ids: String): Block? =
        ids.asSequence()
            .mapNotNull(ResourceLocation::tryParse)
            .filter { it.namespace == namespace }
            .mapNotNull { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) }
            .firstOrNull { it != Blocks.AIR }

    private fun placeLocalDressing(
        level: WorldGenLevel,
        box: BoundingBox,
        center: BlockPos,
        siteSeed: Long,
        definition: ObeliskDefinition
    ) {
        val structures = paletteBlocks(definition, PaletteKind.STRUCTURE, listOf(Blocks.OXIDIZED_CUT_COPPER))
        val decorations = paletteBlocks(definition, PaletteKind.DECORATION, listOf(Blocks.WHITE_CANDLE))
        val minX = maxOf(box.minX(), center.x - SITE_RADIUS)
        val maxX = minOf(box.maxX(), center.x + SITE_RADIUS)
        val minZ = maxOf(box.minZ(), center.z - SITE_RADIUS)
        val maxZ = minOf(box.maxZ(), center.z + SITE_RADIUS)

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                val dx = x - center.x
                val dz = z - center.z
                val squared = dx * dx + dz * dz
                if (squared <= COURT_RADIUS * COURT_RADIUS || squared > SITE_RADIUS * SITE_RADIUS) continue
                val groundY = localGroundY(level, box, x, z) ?: continue
                val ground = BlockPos(x, groundY, z)
                val groundState = level.getBlockState(ground)
                val above = ground.above()
                if (!groundState.fluidState.isEmpty || level.getBlockEntity(ground) != null) continue
                if (!groundState.isFaceSturdy(level, ground, Direction.UP)) continue

                val hash = coordinateHash(siteSeed, x, z)
                val hive = if (definition.id == "bumblezone") hiveColumn(dx, dz) else 0
                if (hive != 0 && isNaturalPathGround(groundState)) {
                    val wax = optionalBlock("the_bumblezone", "the_bumblezone:ancient_wax_bricks")
                        ?: Blocks.HONEYCOMB_BLOCK
                    setBoxed(level, box, ground, preparedState(wax))
                    if (level.getBlockState(above).isAir) {
                        val block = when (hive) {
                            2 -> optionalBlock("the_bumblezone", "the_bumblezone:beehive_beeswax")
                            else -> optionalBlock("the_bumblezone", "the_bumblezone:ancient_wax_compound_eyes")
                        } ?: Blocks.BEEHIVE
                        setBoxed(level, box, above, preparedState(block))
                        if (hive == 2) {
                            val crown = optionalBlock("the_bumblezone", "the_bumblezone:honeycomb_brood_block")
                                ?: Blocks.HONEYCOMB_BLOCK
                            setBoxed(level, box, above.above(), preparedState(crown))
                        }
                    }
                    continue
                }

                if (isRingColumn(dx, dz)) {
                    if (isNaturalPathGround(groundState)) {
                        val block = if (Math.floorMod(hash, 7L) == 0L) Blocks.PACKED_MUD
                            else Blocks.OXIDIZED_CUT_COPPER
                        setBoxed(level, box, ground, block.defaultBlockState())
                        if (isCandleColumn(dx, dz) && level.getBlockState(above).isAir) {
                            setBoxed(level, box, above, candleFor(definition).defaultBlockState()
                                .setValue(BlockStateProperties.LIT, true))
                        }
                    }
                    continue
                }

                if (Math.floorMod(hash, 19L) == 0L && level.getBlockState(above).isAir) {
                    val block = structures[Math.floorMod((hash ushr 8).toInt(), structures.size)]
                    setBoxed(level, box, ground, preparedState(block))
                } else if ((isCandleColumn(dx, dz) || Math.floorMod(hash, 31L) == 0L) &&
                    level.getBlockState(above).isAir
                ) {
                    val block = if (isCandleColumn(dx, dz)) candleFor(definition) else
                        decorations[Math.floorMod((hash ushr 16).toInt(), decorations.size)]
                    val state = preparedState(block)
                    if (state.canSurvive(level, above)) setBoxed(level, box, above, state)
                }
            }
        }
    }

    private fun hiveColumn(dx: Int, dz: Int): Int {
        val centers = listOf(15 to -8, -16 to 7, 7 to 17)
        return centers.maxOf { (x, z) ->
            val squared = (dx - x) * (dx - x) + (dz - z) * (dz - z)
            when {
                squared == 0 -> 2
                squared <= 4 -> 1
                else -> 0
            }
        }
    }

    private fun isCandleColumn(dx: Int, dz: Int): Boolean =
        (abs(dx) == 10 && dz == 0) || (abs(dz) == 10 && dx == 0) ||
            (abs(dx) == 9 && abs(dz) == 4) || (abs(dz) == 9 && abs(dx) == 4)

    private fun candleFor(definition: ObeliskDefinition): Block = when (definition.id) {
        "bumblezone" -> Blocks.YELLOW_CANDLE
        "nether" -> Blocks.RED_CANDLE
        "ratlantis" -> Blocks.GREEN_CANDLE
        "aether" -> Blocks.LIGHT_BLUE_CANDLE
        else -> Blocks.WHITE_CANDLE
    }

    private fun strippedLogForBiome(level: WorldGenLevel, center: BlockPos): Block? {
        val biomeId = level.getBiome(center).unwrapKey().map { it.location() }.orElse(null)
        val path = biomeId?.path ?: ""
        return when {
            "crimson_forest" in path -> Blocks.STRIPPED_CRIMSON_STEM
            "warped_forest" in path -> Blocks.STRIPPED_WARPED_STEM
            biomeId?.namespace == "aether" && path.startsWith("skyroot_") ->
                optionalBlock("aether", "aether:stripped_skyroot_log")
            biomeId?.namespace == "rats" && path == "ratlantis" -> Blocks.STRIPPED_JUNGLE_LOG
            "mangrove" in path -> Blocks.STRIPPED_MANGROVE_LOG
            "cherry" in path -> Blocks.STRIPPED_CHERRY_LOG
            "dark_forest" in path || "dark_wood" in path -> Blocks.STRIPPED_DARK_OAK_LOG
            "birch" in path -> Blocks.STRIPPED_BIRCH_LOG
            "taiga" in path || "spruce" in path || path == "grove" || "snowy_grove" in path -> Blocks.STRIPPED_SPRUCE_LOG
            "jungle" in path -> Blocks.STRIPPED_JUNGLE_LOG
            "savanna" in path || "acacia" in path -> Blocks.STRIPPED_ACACIA_LOG
            "forest" in path || "woods" in path || "woodland" in path ||
                path == "plains" || path == "sunflower_plains" || path == "meadow" ||
                "swamp" in path -> Blocks.STRIPPED_OAK_LOG
            else -> null
        }
    }

    private fun localGroundY(level: WorldGenLevel, box: BoundingBox, x: Int, z: Int): Int? {
        if (x !in box.minX()..box.maxX() || z !in box.minZ()..box.maxZ()) return null
        val firstFree = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z)
        val y = firstFree - 1
        return y.takeIf { it in box.minY()..box.maxY() }
    }

    private fun isNaturalPathGround(state: BlockState): Boolean =
        state.`is`(BlockTags.DIRT) ||
            state.`is`(BlockTags.SAND) ||
            state.`is`(BlockTags.BASE_STONE_OVERWORLD) ||
            state.`is`(BlockTags.BASE_STONE_NETHER) ||
            state.`is`(Blocks.PACKED_MUD)

    private fun setBoxed(level: WorldGenLevel, box: BoundingBox, pos: BlockPos, state: BlockState): Boolean =
        box.isInside(pos) && level.setBlock(pos, state, UPDATE_FLAGS)

    private fun courtState(siteSeed: Long, pos: BlockPos): BlockState {
        val block = if (Math.floorMod(coordinateHash(siteSeed, pos.x, pos.z, 0x43a7L), 5L) == 0L) {
            Blocks.PACKED_MUD
        } else {
            Blocks.OXIDIZED_CUT_COPPER
        }
        return if (block == Blocks.PACKED_MUD) block.defaultBlockState() else copperState(block, pos, null)
    }

    private fun copperState(block: Block, pos: BlockPos, center: BlockPos?): BlockState {
        val key = BuiltInRegistries.BLOCK.getKey(block)
        val path = key.path
        val oxidized = when {
            block == Blocks.RAW_COPPER_BLOCK || path == "copper_block" ||
                path == "exposed_copper" || path == "weathered_copper" -> Blocks.OXIDIZED_COPPER
            path == "cut_copper" || path == "exposed_cut_copper" ||
                path == "weathered_cut_copper" -> Blocks.OXIDIZED_CUT_COPPER
            path.contains("copper") && !path.contains("oxidized") -> {
                val suffix = path.removePrefix("exposed_").removePrefix("weathered_")
                BuiltInRegistries.BLOCK.getOptional(ResourceLocation(key.namespace, "oxidized_$suffix"))
                    .orElse(Blocks.OXIDIZED_CUT_COPPER)
            }
            else -> block
        }
        return oxidized.defaultBlockState()
    }

    private fun preparedState(block: Block): BlockState {
        var state = copperState(block, BlockPos.ZERO, null)
        if (state.hasProperty(BlockStateProperties.LIT)) state = state.setValue(BlockStateProperties.LIT, true)
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE)) state = state.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
        if (state.hasProperty(BlockStateProperties.FACING)) state = state.setValue(BlockStateProperties.FACING, Direction.UP)
        return state
    }

    private enum class PaletteKind { PATH, STRUCTURE, DECORATION, TROPHY }

    private fun paletteBlocks(definition: ObeliskDefinition, kind: PaletteKind, fallback: List<Block>): List<Block> {
        val configured = definition.cultivationPalette ?: definition.graveyardPalette
        val ids = when (kind) {
            PaletteKind.PATH -> configured?.pathBlocks ?: definition.pathBlocks
            PaletteKind.STRUCTURE -> configured?.structureBlocks ?: definition.structureBlocks
            PaletteKind.DECORATION -> configured?.decorations ?: definition.decorations
            PaletteKind.TROPHY -> configured?.trophyBlocks ?: definition.trophyBlocks
        }
        val resolved = ids.orEmpty().mapNotNull { id ->
            val key = ResourceLocation.tryParse(id) ?: return@mapNotNull null
            BuiltInRegistries.BLOCK.getOptional(key).orElse(null)?.takeUnless { it == Blocks.AIR }
        }
        return resolved.ifEmpty { fallback }
    }
}
