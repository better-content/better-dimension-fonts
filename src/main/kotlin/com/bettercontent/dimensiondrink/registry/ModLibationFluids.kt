package com.bettercontent.dimensiondrink.registry

import com.bettercontent.dimensiondrink.MOD_ID
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.BucketItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FlowingFluid
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions
import net.minecraftforge.common.SoundActions
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fluids.FluidType
import net.minecraftforge.fluids.ForgeFlowingFluid
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import java.util.function.Consumer

/** Four renewable, destination-specific Create mixing fluids for the overhead Font Pourer. */
object ModLibationFluids {
    private val types = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, MOD_ID)
    private val fluids = DeferredRegister.create(ForgeRegistries.FLUIDS, MOD_ID)
    private val still = ResourceLocation("minecraft", "block/water_still")
    private val flowingTexture = ResourceLocation("minecraft", "block/water_flow")

    private val entries = linkedMapOf<String, RegistryObject<FlowingFluid>>()

    fun bootstrap() {
        if (entries.isNotEmpty()) return
        registerFluid("nether_libation", 0xffab3c20.toInt())
        registerFluid("aether_libation", 0xff78ddec.toInt())
        registerFluid("bumblezone_libation", 0xffe8ad24.toInt())
        registerFluid("ratlantis_libation", 0xff79b58c.toInt())
    }

    fun register(bus: IEventBus) {
        types.register(bus)
        fluids.register(bus)
    }

    fun definitionFor(fluid: Fluid): String? = entries.entries.firstOrNull { it.value.get() === fluid }
        ?.key?.removeSuffix("_libation")

    private fun registerFluid(name: String, tint: Int) {
        val type = types.register(name) {
            object : FluidType(FluidType.Properties.create().density(1100).viscosity(1500)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)) {
                override fun initializeClient(consumer: Consumer<IClientFluidTypeExtensions>) {
                    consumer.accept(object : IClientFluidTypeExtensions {
                        override fun getStillTexture(): ResourceLocation = still
                        override fun getFlowingTexture(): ResourceLocation = flowingTexture
                        override fun getTintColor(): Int = tint
                    })
                }
            }
        }
        lateinit var source: RegistryObject<FlowingFluid>
        lateinit var flowing: RegistryObject<FlowingFluid>
        lateinit var block: RegistryObject<LiquidBlock>
        lateinit var bucket: RegistryObject<Item>
        val properties = ForgeFlowingFluid.Properties(type, { source.get() }, { flowing.get() })
            .block { block.get() }.bucket { bucket.get() }
        source = fluids.register(name) { ForgeFlowingFluid.Source(properties) }
        flowing = fluids.register("flowing_$name") { ForgeFlowingFluid.Flowing(properties) }
        block = ModBlocks.REGISTRY.register(name) {
            LiquidBlock({ source.get() }, BlockBehaviour.Properties.of().noCollission().strength(100.0f).noLootTable())
        }
        bucket = ModItems.REGISTRY.register("${name}_bucket") {
            BucketItem({ source.get() }, Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1))
        }
        entries[name] = source
    }
}
