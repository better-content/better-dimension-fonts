package com.bettercontent.betterdimensionfonts.worldgen.village

import com.mojang.datafixers.util.Pair
import com.bettercontent.betterdimensionfonts.MOD_ID
import com.bettercontent.betterdimensionfonts.mixin.StructureTemplatePoolAccessor
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool
import net.minecraftforge.event.server.ServerAboutToStartEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

object VillageShrinePools {
    // Twelve house attempts give roughly one shrine per five newly generated villages.
    private const val TARGET_ATTEMPT_RATE = 0.01842347
    private const val SHRINE_WEIGHT = 2

    data class ShrinePoolTarget(
        val style: String,
        val poolId: ResourceLocation,
        val templateId: ResourceLocation,
        val basePoolWeight: Int,
        val shrineWeight: Int,
        val placementChance: Float
    ) {
        fun estimatedAttemptRate(): Double = shrineWeight.toDouble() / (basePoolWeight + shrineWeight) * placementChance
    }

    val TARGETS: List<ShrinePoolTarget> = listOf(
        target("plains", 87),
        target("desert", 72),
        target("savanna", 81),
        target("snowy", 68),
        target("taiga", 76)
    )

    @SubscribeEvent
    fun onServerAboutToStart(event: ServerAboutToStartEvent) {
        val templatePools = event.server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)
        TARGETS.forEach { target ->
            templatePools.get(target.poolId)?.let { pool ->
                appendShrineIfMissing(pool, target)
            }
        }
    }

    private fun appendShrineIfMissing(pool: StructureTemplatePool, target: ShrinePoolTarget) {
        val access = pool as StructureTemplatePoolAccessor
        val rawTemplates = access.dimensionDrinkRawTemplates.toMutableList()
        if (rawTemplates.any { pair ->
                val element = pair.first
                element is ChanceLegacySinglePoolElement && element.matchesLocation(target.templateId)
            }) {
            return
        }

        val shrine = ChanceLegacySinglePoolElement(
            location = target.templateId,
            placementChance = target.placementChance,
            projection = StructureTemplatePool.Projection.RIGID
        )
        rawTemplates.add(Pair.of(shrine, SHRINE_WEIGHT))
        access.dimensionDrinkRawTemplates = rawTemplates

        val expandedTemplates: ObjectArrayList<StructurePoolElement> = access.dimensionDrinkTemplates
        repeat(SHRINE_WEIGHT) {
            expandedTemplates.add(shrine)
        }
        access.setDimensionDrinkMaxSize(Int.MIN_VALUE)
    }

    private fun target(style: String, basePoolWeight: Int): ShrinePoolTarget {
        val totalWeight = basePoolWeight + SHRINE_WEIGHT
        val chance = (TARGET_ATTEMPT_RATE * totalWeight / SHRINE_WEIGHT).toFloat().coerceAtMost(1.0f)
        return ShrinePoolTarget(
            style = style,
            poolId = ResourceLocation.fromNamespaceAndPath("minecraft", "village/$style/houses"),
            templateId = ResourceLocation.fromNamespaceAndPath(MOD_ID, "village/font_shrine"),
            basePoolWeight = basePoolWeight,
            shrineWeight = SHRINE_WEIGHT,
            placementChance = chance
        )
    }
}
