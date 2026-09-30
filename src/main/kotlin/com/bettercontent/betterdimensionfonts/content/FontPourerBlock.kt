package com.bettercontent.betterdimensionfonts.content

import com.bettercontent.betterdimensionfonts.registry.ModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.fluids.FluidUtil

/** Only an overhead Pourer can feed a running Font; the intervening block stays open air. */
class FontPourerBlock(properties: Properties) : Block(properties), EntityBlock {
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = FontPourerBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (type != ModBlockEntities.FONT_POURER.get()) return null
        return BlockEntityTicker { tickLevel, _, _, entity ->
            if (!tickLevel.isClientSide && entity is FontPourerBlockEntity) entity.serverTick()
        }
    }

    override fun use(state: BlockState, level: Level, pos: BlockPos, player: Player,
                     hand: InteractionHand, hit: BlockHitResult): InteractionResult {
        val pourer = level.getBlockEntity(pos) as? FontPourerBlockEntity ?: return InteractionResult.PASS
        return if (FluidUtil.interactWithFluidHandler(player, hand, pourer.fluidHandler()))
            InteractionResult.sidedSuccess(level.isClientSide) else InteractionResult.PASS
    }

    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        val pourer = level.getBlockEntity(pos) as? FontPourerBlockEntity ?: return
        if (!pourer.isPouring()) return
        repeat(3) {
            level.addParticle(ParticleTypes.DRIPPING_WATER,
                pos.x + 0.5 + (random.nextDouble() - 0.5) * 0.16,
                pos.y - random.nextDouble() * 1.5,
                pos.z + 0.5 + (random.nextDouble() - 0.5) * 0.16,
                0.0, -0.08, 0.0)
        }
    }
}
