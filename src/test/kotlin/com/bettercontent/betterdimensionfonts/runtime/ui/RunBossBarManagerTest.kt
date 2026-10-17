package com.bettercontent.betterdimensionfonts.runtime.ui

import net.minecraft.world.BossEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunBossBarManagerTest {
    @Test
    fun `open ways reports free until dawn even at zero or full charge`() {
        for (charge in listOf(0.0, 0.2, 0.9, 1.0)) {
            assertEquals(FontBossBarPresentation(1.0f, "Open Ways: free until dawn", BossEvent.BossBarColor.YELLOW),
                RunBossBarManager.presentation(charge, openWays = true))
        }
        assertEquals("Font Charge: 0%", RunBossBarManager.presentation(0.0, openWays = false)?.title)
    }

    @Test
    fun `dawn warnings use the remaining overworld tick boundaries`() {
        for (ticks in listOf(-1L, 0L, 1001L, 12000L)) assertEquals(0, RunBossBarManager.warningStage(ticks))
        for (ticks in listOf(1000L, 999L, 201L)) assertEquals(1, RunBossBarManager.warningStage(ticks))
        for (ticks in listOf(200L, 199L, 1L)) assertEquals(2, RunBossBarManager.warningStage(ticks))
    }

    @Test
    fun `bar is hidden until charge falls below ninety percent`() {
        assertNull(RunBossBarManager.presentation(1.0))
        assertNull(RunBossBarManager.presentation(0.90))
        assertEquals(0.899f, RunBossBarManager.presentation(0.899)?.progress)
    }

    @Test
    fun `bar reports remaining charge with configured warning colors`() {
        assertEquals(
            FontBossBarPresentation(0.75f, "Font Charge: 75%", BossEvent.BossBarColor.GREEN),
            RunBossBarManager.presentation(0.75)
        )
        assertEquals(BossEvent.BossBarColor.YELLOW, RunBossBarManager.presentation(0.40)?.color)
        assertEquals(BossEvent.BossBarColor.RED, RunBossBarManager.presentation(0.20)?.color)
    }
}
