package com.bettercontent.betterdimensionfonts.client

import kotlin.math.cos
import kotlin.math.sin

/** Bounded local offsets; no world scan, tickets, block writes, or charge-shaped fake reservoir. */
object OpenWaysParticles {
    data class Mote(val x: Double, val y: Double, val z: Double, val vx: Double, val vy: Double, val vz: Double, val gold: Boolean)

    fun frame(tick: Long): List<Mote> {
        if (tick % 4L != 0L) return emptyList()
        return buildList {
            repeat(4) { arm ->
                val angle = arm * Math.PI / 2.0 + tick * 0.025
                val radius = 1.2 + (tick % 40L) / 40.0
                add(Mote(cos(angle) * radius, 0.2, sin(angle) * radius,
                    -cos(angle) * 0.12, 0.025, -sin(angle) * 0.12, arm % 2 == 0))
            }
            repeat(2) { side ->
                val angle = tick * 0.12 + side * Math.PI
                add(Mote(cos(angle) * 0.17, 0.65, sin(angle) * 0.17, 0.0, 0.085, 0.0, side == 0))
            }
            if (tick % 20L == 0L) repeat(12) { point ->
                val angle = point * Math.PI / 6.0
                add(Mote(cos(angle) * 0.45, 0.45, sin(angle) * 0.45,
                    cos(angle) * 0.055, 0.012, sin(angle) * 0.055, point % 2 == 0))
            }
        }
    }
}
