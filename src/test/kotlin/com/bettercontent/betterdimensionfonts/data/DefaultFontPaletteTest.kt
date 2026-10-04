package com.bettercontent.betterdimensionfonts.data

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DefaultFontPaletteTest {
    @Test
    fun bundledDefaultFontPalettesDoNotRequestBareEmptyFlowerPots() {
        val resourceNames = listOf(
            "overworld.json",
            "bumblezone.json",
            "nether.json"
        )
        resourceNames.forEach { name ->
            val stream = javaClass.classLoader.getResourceAsStream("defaults/fonts/$name")
                ?: error("Missing bundled default font resource $name")
            val root = stream.reader().use { JsonParser.parseReader(it).asJsonObject }
            val palette = root.getAsJsonObject("cultivationPalette")
            val decorations = palette.getAsJsonArray("decorations")
            val trophies = palette.getAsJsonArray("trophyBlocks")
            assertFalse(
                decorations.any { it.asString == "minecraft:flower_pot" },
                "Expected $name decorations to avoid bare empty flower pots"
            )
            if (trophies != null) {
                assertFalse(
                    trophies.any { it.asString == "minecraft:flower_pot" },
                    "Expected $name trophyBlocks to avoid bare empty flower pots"
                )
            }
        }
    }

    @Test
    fun bundledFontsRefillFromEmptyInTenMinutesAndOldBuiltInDefaultsMigrate() {
        val resourceNames = listOf("overworld", "nether", "end", "aether", "bumblezone")
        resourceNames.forEach { id ->
            val stream = javaClass.classLoader.getResourceAsStream("defaults/fonts/$id.json")
                ?: error("Missing bundled default font resource $id")
            val root = stream.reader().use { JsonParser.parseReader(it).asJsonObject }
            val maxCharge = root.get("maxCharge").asDouble
            val passiveRate = root.get("passiveChargePerTick").asDouble
            assertEquals(1.25, passiveRate, "Expected $id to use the 10-minute passive refill rate")
            assertEquals(600.0, maxCharge / (passiveRate * 20.0), "Expected $id to refill in 600 in-game seconds")
            assertEquals(1.25, FontDefinitionMigrations.migrateLegacyRate(id, 0.25))
        }
        assertEquals(0.25, FontDefinitionMigrations.migrateLegacyRate("custom_font", 0.25))
        assertEquals(0.5, FontDefinitionMigrations.migrateLegacyRate("nether", 0.5))
    }
}
