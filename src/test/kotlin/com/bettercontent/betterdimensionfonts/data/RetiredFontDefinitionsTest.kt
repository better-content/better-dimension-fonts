package com.bettercontent.betterdimensionfonts.data

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetiredFontDefinitionsTest {
    @Test
    fun staleGeneratedRatlantisDefinitionIsRejectedEvenWithRatsInstalled() {
        val legacy = JsonParser.parseString("""
            {"id":"ratlantis","requiredNamespace":"rats","targetDimension":"rats:ratlantis",
             "instanceTemplateId":"ratlantis","enabled":true,"worldgenWeight":1.0}
        """).asJsonObject
        assertTrue(RetiredFontDefinitions.isRetired(
            legacy.get("id").asString,
            legacy.get("targetDimension").asString,
            legacy.get("instanceTemplateId").asString
        ))
        assertNull(javaClass.classLoader.getResource("defaults/fonts/ratlantis.json"))
    }

    @Test
    fun renamedAndLegacyTargetAliasesCannotRestoreRetiredDestination() {
        assertTrue(RetiredFontDefinitions.isRetired("custom", "rats:ratlantis", null))
        assertTrue(RetiredFontDefinitions.isRetired("custom", null, "ratlantis"))
        assertTrue(RetiredFontDefinitions.isRetired("custom", null, "rats:ratlantis"))
        assertTrue(RetiredFontDefinitions.isRetired(" ratlantis ", null, null))
        assertTrue(RetiredFontDefinitions.isRetired("custom", " ratlantis ", null))
    }

    @Test
    fun remainingFontsAndOtherRatsDestinationsAreNotRetired() {
        for (id in listOf("overworld", "nether", "end", "aether", "bumblezone")) {
            assertFalse(RetiredFontDefinitions.isRetired(id, "minecraft:$id", id))
        }
        assertFalse(RetiredFontDefinitions.isRetired("custom", "rats:custom", null))
        assertFalse(RetiredFontDefinitions.isRetired(null, null, null))
    }
}
