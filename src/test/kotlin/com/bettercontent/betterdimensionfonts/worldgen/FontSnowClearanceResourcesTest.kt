package com.bettercontent.betterdimensionfonts.worldgen

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class FontSnowClearanceResourcesTest {
    @Test
    fun `font interaction clearance uses additive native snow support tag`() {
        val tag = JsonParser.parseString(Files.readString(Path.of(
            "src/main/resources/data/minecraft/tags/blocks/snow_layer_cannot_survive_on.json"
        ))).asJsonObject
        assertFalse(tag.get("replace").asBoolean, "Retain vanilla and other mods' snow support exclusions")
        assertEquals(listOf("better_dimension_fonts:dimensional_font"), tag.getAsJsonArray("values").map { it.asString })
    }
}
