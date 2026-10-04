package com.bettercontent.betterdimensionfonts.data

import com.bettercontent.betterdimensionfonts.ObeliskConstants
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

internal object FontDefinitionMigrations {
    private const val LEGACY_PASSIVE_CHARGE_PER_TICK = 0.25
    private const val MIGRATION_MARKER = ".passive-charge-10-minute-defaults-v1"
    private val builtInFonts = setOf("overworld", "nether", "end", "aether", "bumblezone")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun migrateLegacyPassiveChargeDefaults(configRoot: Path) {
        val marker = configRoot.resolve(MIGRATION_MARKER)
        if (marker.exists()) return

        val definitions = configRoot.resolve("fonts")
        builtInFonts.forEach { id ->
            val path = definitions.resolve("$id.json")
            if (!path.exists()) return@forEach
            val definition = path.inputStream().bufferedReader().use {
                JsonParser.parseReader(it).asJsonObject
            }
            val passiveRate = definition.get("passiveChargePerTick")?.asDouble ?: return@forEach
            val migratedRate = migrateLegacyRate(id, passiveRate)
            if (migratedRate != passiveRate) {
                definition.addProperty("passiveChargePerTick", migratedRate)
                path.outputStream().bufferedWriter().use { it.write(gson.toJson(definition)) }
            }
        }

        marker.writeText("Better Dimension Fonts built-in passive Font charge defaults migrated to 10-minute refill rate.\n")
    }

    internal fun migrateLegacyRate(fontId: String, configuredRate: Double): Double =
        if (fontId in builtInFonts && configuredRate == LEGACY_PASSIVE_CHARGE_PER_TICK) {
            ObeliskConstants.PASSIVE_CHARGE_PER_TICK
        } else {
            configuredRate
        }
}
