package com.bettercontent.dimensiondrink.content

import com.bettercontent.dimensiondrink.registry.ModBlockEntities
import com.bettercontent.dimensiondrink.registry.ModBlocks
import com.bettercontent.dimensiondrink.registry.ModLibationFluids
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.fluids.capability.templates.FluidTank

class FontPourerBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(ModBlockEntities.FONT_POURER.get(), pos, state) {
    private val tank = object : FluidTank(4000) {
        override fun isFluidValid(stack: FluidStack): Boolean =
            stack.isEmpty || ModLibationFluids.definitionFor(stack.fluid) != null

        override fun onContentsChanged() {
            setChanged()
            level?.sendBlockUpdated(blockPos, blockState, blockState, 2)
        }
    }
    private var fluidCapability: LazyOptional<IFluidHandler> = LazyOptional.of { tank }
    private var tickPhase = 0

    fun fluidHandler(): IFluidHandler = tank

    fun isPouring(): Boolean {
        val current = level ?: return false
        val font = current.getBlockEntity(blockPos.below(2)) as? ObeliskBlockEntity ?: return false
        return current.getBlockState(blockPos.below()).isAir && !tank.isEmpty && font.isRunActive() &&
            font.definitionId == ModLibationFluids.definitionFor(tank.fluid.fluid)
    }

    fun serverTick() {
        if (++tickPhase < 12) return
        tickPhase = 0
        if (!isPouring()) return
        val font = level?.getBlockEntity(blockPos.below(2)) as? ObeliskBlockEntity ?: return
        if (font.restoreRunCharge(48) == 48) tank.drain(1, IFluidHandler.FluidAction.EXECUTE)
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        tank.readFromNBT(tag.getCompound("Libation"))
        tickPhase = tag.getInt("TickPhase").coerceIn(0, 11)
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        tag.put("Libation", tank.writeToNBT(CompoundTag()))
        tag.putInt("TickPhase", tickPhase)
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun <T : Any> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> =
        if (!remove && cap === ForgeCapabilities.FLUID_HANDLER) fluidCapability.cast() else super.getCapability(cap, side)

    override fun invalidateCaps() {
        super.invalidateCaps()
        fluidCapability.invalidate()
    }

    override fun reviveCaps() {
        super.reviveCaps()
        fluidCapability = LazyOptional.of { tank }
    }
}
