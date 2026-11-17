package com.bettercontent.betterdimensionfonts.worldgen

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property

/** New Font construction is masonry, not a source of harvestable copper.
 * Applied only by placement code: never rewrites blocks in an existing world.
 */
internal object FontConstructionPalette {
    fun block(original: Block): Block {
        val path = BuiltInRegistries.BLOCK.getKey(original).path
        if (!path.contains("copper")) return original
        return when {
            original is StairBlock -> Blocks.DEEPSLATE_TILE_STAIRS
            original is SlabBlock -> Blocks.DEEPSLATE_TILE_SLAB
            path.contains("shingle") || path.contains("tile") -> Blocks.DEEPSLATE_TILES
            path.contains("lantern") -> if (path.contains("soul")) Blocks.SOUL_LANTERN else Blocks.LANTERN
            path.contains("bars") -> Blocks.IRON_BARS
            path.contains("chain") -> Blocks.CHAIN
            path.contains("trapdoor") -> Blocks.IRON_TRAPDOOR
            path.contains("ladder") -> Blocks.LADDER
            path.contains("rail") -> Blocks.RAIL
            path.contains("cauldron") -> Blocks.CAULDRON
            else -> Blocks.STONE_BRICKS
        }
    }

    fun state(original: BlockState): BlockState {
        val replacement = block(original.block)
        if (replacement == original.block) return original
        var result = replacement.defaultBlockState()
        for (property in original.properties) result = copyProperty(original, result, property)
        return result
    }

    private fun <T : Comparable<T>> copyProperty(source: BlockState, target: BlockState, property: Property<T>): BlockState =
        if (target.hasProperty(property)) target.setValue(property, source.getValue(property)) else target
}
