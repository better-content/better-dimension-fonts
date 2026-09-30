package com.bettercontent.betterdimensionfonts

import com.mojang.logging.LogUtils
import com.bettercontent.betterdimensionfonts.data.ObeliskDataManager
import com.bettercontent.betterdimensionfonts.commands.ObeliskCommands
import com.bettercontent.betterdimensionfonts.content.FontHarvestEvents
import com.bettercontent.betterdimensionfonts.gametest.ObeliskGameTestRegistrar
import com.bettercontent.betterdimensionfonts.registry.ModRegistries
import com.bettercontent.betterdimensionfonts.runtime.player.VanillaPortalBlocker
import com.bettercontent.betterdimensionfonts.runtime.player.FontCompanions
import com.bettercontent.betterdimensionfonts.runtime.run.FontChunkTicketManager
import com.bettercontent.betterdimensionfonts.runtime.run.RunRegistry
import com.bettercontent.betterdimensionfonts.runtime.ui.RunBossBarManager
import com.bettercontent.betterdimensionfonts.trade.DimensionalFontMapTrades
import com.bettercontent.betterdimensionfonts.worldgen.village.VillageShrinePools
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

@Mod(MOD_ID)
class DimensionDrinkMod {

    init {
        LOGGER.info("Starting {}", MOD_NAME)
        ObeliskDataManager.ensureLoaded()
        val modBus = FMLJavaModLoadingContext.get().modEventBus
        ModRegistries.registerAll(modBus)
        modBus.register(ObeliskGameTestRegistrar)
        modBus.addListener(::onCommonSetup)
        MinecraftForge.EVENT_BUS.register(VanillaPortalBlocker)
        MinecraftForge.EVENT_BUS.register(FontCompanions)
        MinecraftForge.EVENT_BUS.register(FontHarvestEvents)
        MinecraftForge.EVENT_BUS.register(RunRegistry)
        MinecraftForge.EVENT_BUS.register(RunBossBarManager)
        MinecraftForge.EVENT_BUS.register(ObeliskCommands)
        MinecraftForge.EVENT_BUS.register(DimensionalFontMapTrades)
        MinecraftForge.EVENT_BUS.register(VillageShrinePools)
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork(FontChunkTicketManager::registerValidationCallback)
    }

    companion object {
        private val LOGGER = LogUtils.getLogger()
    }
}
