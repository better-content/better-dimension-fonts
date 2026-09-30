package com.bettercontent.betterdimensionfonts.runtime.player

import com.bettercontent.betterdimensionfonts.trade.TestMinecraftBootstrap
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.util.UUID

class CompanionSavedDataTest {
    @BeforeTest
    fun bootstrap() = TestMinecraftBootstrap.bootstrap()

    @Test
    fun persistsUnlimitedBeehemothsAndPendingReturnsAcrossReload() {
        val data = CompanionSavedData.load(CompoundTag().apply { putInt("schema", 1) })
        val owner = UUID.randomUUID()
        val bumblezone = key("the_bumblezone:the_bumblezone")
        val overworld = Level.OVERWORLD
        repeat(128) { index ->
            data.put(CompanionRecord(UUID.randomUUID(), owner, bumblezone,
                BlockPos(index * 20, 90, index * 20), true, -1, overworld, BlockPos(0, 65, 0)))
        }
        val restored = CompanionSavedData.load(data.save(CompoundTag()))
        assertEquals(128, restored.all().size)
        assertEquals(setOf(owner), restored.all().map { it.ownerId }.toSet())
        assertEquals(setOf(overworld), restored.all().map { it.returnDimension }.toSet())
    }

    @Test
    fun markTierAndPositionSurviveSaveAndMove() {
        val data = CompanionSavedData.load(CompoundTag().apply { putInt("schema", 1) })
        val id = UUID.randomUUID()
        val record = CompanionRecord(id, UUID.randomUUID(), Level.NETHER, BlockPos(4, 70, 9), false, 2)
        data.put(record)
        data.put(record.copy(pos = BlockPos(1000, 72, 1100)))
        assertEquals(BlockPos(1000, 72, 1100), CompanionSavedData.load(data.save(CompoundTag())).get(id)?.pos)
        assertEquals(2, CompanionSavedData.load(data.save(CompoundTag())).get(id)?.tier)
    }

    @Test
    fun refusesUnknownSchema() {
        assertFailsWith<IllegalArgumentException> {
            CompanionSavedData.load(CompoundTag().apply { putInt("schema", 2) })
        }
    }

    private fun key(id: String): ResourceKey<Level> =
        ResourceKey.create(Registries.DIMENSION, ResourceLocation(id))
}
