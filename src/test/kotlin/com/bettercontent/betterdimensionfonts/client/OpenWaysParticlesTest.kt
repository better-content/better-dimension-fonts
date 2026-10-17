package com.bettercontent.betterdimensionfonts.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenWaysParticlesTest {
    @Test
    fun `streams plume and pulses are bounded and deterministic`() {
        for (tick in 0L..24000L) {
            val motes = OpenWaysParticles.frame(tick)
            assertEquals(if (tick % 4L != 0L) 0 else if (tick % 20L == 0L) 18 else 6, motes.size)
            assertTrue(motes.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
            assertTrue(motes.all { it.x * it.x + it.z * it.z <= 4.85 && it.y in 0.0..1.0 })
            assertTrue(motes.all { kotlin.math.abs(it.vx) <= 0.12 && it.vy in 0.0..0.1 && kotlin.math.abs(it.vz) <= 0.12 })
            assertEquals(motes.size / 2, motes.count { it.gold })
        }
        assertEquals(42, (0L until 20L).sumOf { OpenWaysParticles.frame(it).size })
        assertEquals(OpenWaysParticles.frame(12000), OpenWaysParticles.frame(12000))
    }
}
