package com.bettercontent.dimensiondrink.registry

import com.bettercontent.dimensiondrink.MOD_ID
import com.bettercontent.dimensiondrink.content.ObeliskBlockEntity
import com.bettercontent.dimensiondrink.content.FontPourerBlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ModBlockEntities {
    val REGISTRY: DeferredRegister<BlockEntityType<*>> = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID)

    val OBELISK: RegistryObject<BlockEntityType<ObeliskBlockEntity>> = REGISTRY.register("dimensional_font") {
        BlockEntityType.Builder.of(::ObeliskBlockEntity, ModBlocks.OBELISK.get(), ModBlocks.RETURN_FONT.get()).build(null)
    }
    val FONT_POURER: RegistryObject<BlockEntityType<FontPourerBlockEntity>> = REGISTRY.register("font_pourer") {
        BlockEntityType.Builder.of(::FontPourerBlockEntity, ModBlocks.FONT_POURER.get()).build(null)
    }
}
