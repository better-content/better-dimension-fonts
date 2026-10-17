package com.bettercontent.betterdimensionfonts.gametest

import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraftforge.gametest.PrefixGameTestTemplate

/** Explicit provider-present supplement, never silently registered/skipped in the absent-provider gate. */
@PrefixGameTestTemplate(false)
class ObeliskCelestialGameTests {
    @GameTest(templateNamespace = "better_dimension_fonts", template = "bootstrap/empty", batch = "celestial_locator", timeoutTicks = 100)
    fun natural_locator_reads_index_without_loading_chunks(helper: GameTestHelper) {
        LocatorCheck.run(helper)
    }

    private object LocatorCheck {
        fun run(helper: GameTestHelper) {
            val level = helper.level
            val locator = com.bettercontent.celestial.api.CelestialEventsApi.fontLocator().orElseThrow()
            val pos = helper.absolutePos(net.minecraft.core.BlockPos(3, 2, 3))
            level.setBlock(pos, com.bettercontent.betterdimensionfonts.registry.ModBlocks.OBELISK.get().defaultBlockState(), 3)
            val font = level.getBlockEntity(pos) as com.bettercontent.betterdimensionfonts.content.ObeliskBlockEntity
            font.initializeGeneratedFont("nether", 15000.0)
            val target = locator.nearest(level, pos, 0).orElseThrow()
            helper.assertTrue(target.position() == pos && target.definitionId() == "nether", "Expected indexed natural Font")
            helper.assertTrue(locator.isValid(level, target), "Loaded natural Font is valid")
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3)
            helper.assertTrue(!locator.isValid(level, target), "Harvested loaded target is invalid")
            val far = net.minecraft.core.BlockPos(1000000, 70, 1000000)
            val index = com.bettercontent.betterdimensionfonts.trade.FontLocationSavedData.get(level.server)
            index.register(level, far, "nether")
            val before = level.chunkSource.loadedChunksCount
            helper.assertTrue(level.chunkSource.getChunkNow(far.x shr 4, far.z shr 4) == null, "Fixture target must be unloaded")
            val unknown = locator.nearest(level, far, 0).orElseThrow()
            helper.assertTrue(locator.isValid(level, unknown), "Unloaded indexed target is unknown, not invalid")
            helper.assertTrue(level.chunkSource.loadedChunksCount == before && level.chunkSource.getChunkNow(far.x shr 4, far.z shr 4) == null, "Locator must not load or generate chunks")
            index.unregister(level.dimension(), far)
            helper.succeed()
        }
    }

    @GameTest(templateNamespace = "better_dimension_fonts", template = "bootstrap/empty", batch = "celestial_new", timeoutTicks = 220)
    fun open_ways_opens_empty_font_and_extracts_at_dawn(helper: GameTestHelper) {
        ObeliskGameTestSupport.openWaysEconomy(helper, existing = false)
    }

    @GameTest(templateNamespace = "better_dimension_fonts", template = "bootstrap/empty", batch = "celestial_existing", timeoutTicks = 220)
    fun open_ways_pauses_existing_session_and_pourer(helper: GameTestHelper) {
        ObeliskGameTestSupport.openWaysEconomy(helper, existing = true)
    }
}
