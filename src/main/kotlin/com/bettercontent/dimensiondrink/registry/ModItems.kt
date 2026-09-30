package com.bettercontent.dimensiondrink.registry

import com.bettercontent.dimensiondrink.MOD_ID
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import com.bettercontent.dimensiondrink.runtime.player.DimensionalCollarItem
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ModItems {
    val REGISTRY: DeferredRegister<Item> = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID)

    val OBELISK: RegistryObject<Item> = REGISTRY.register("dimensional_font") { BlockItem(ModBlocks.OBELISK.get(), Item.Properties()) }
    val RETURN_FONT: RegistryObject<Item> = REGISTRY.register("return_seal") { BlockItem(ModBlocks.RETURN_FONT.get(), Item.Properties()) }
    val RETURN_PAD: RegistryObject<Item> = RETURN_FONT
    val FONT_POURER: RegistryObject<Item> = REGISTRY.register("font_pourer") { BlockItem(ModBlocks.FONT_POURER.get(), Item.Properties()) }
    val CANVAS_COLLAR: RegistryObject<Item> = REGISTRY.register("canvas_collar") { DimensionalCollarItem(0) }
    val IRON_COLLAR: RegistryObject<Item> = REGISTRY.register("iron_collar") { DimensionalCollarItem(1) }
    val DIAMOND_COLLAR: RegistryObject<Item> = REGISTRY.register("diamond_collar") { DimensionalCollarItem(2) }
}
