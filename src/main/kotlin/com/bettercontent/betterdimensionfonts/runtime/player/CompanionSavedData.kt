package com.bettercontent.betterdimensionfonts.runtime.player

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

data class CompanionRecord(
    val entityId: UUID,
    val ownerId: UUID,
    val dimension: ResourceKey<Level>,
    val pos: BlockPos,
    val beehemoth: Boolean,
    val tier: Int = -1,
    val returnDimension: ResourceKey<Level>? = null,
    val returnPos: BlockPos? = null
)

/** Persistent location index; a chunk can be loaded only for a recorded owned mob. */
class CompanionSavedData private constructor(
    private val records: LinkedHashMap<UUID, CompanionRecord> = linkedMapOf()
) : SavedData() {
    fun get(id: UUID): CompanionRecord? = records[id]
    fun all(): List<CompanionRecord> = records.values.toList()

    fun put(record: CompanionRecord) {
        if (records.put(record.entityId, record) != record) setDirty()
    }

    fun remove(id: UUID) {
        if (records.remove(id) != null) setDirty()
    }

    override fun save(tag: CompoundTag): CompoundTag {
        tag.putInt("schema", 1)
        val list = ListTag()
        records.values.forEach { record ->
            list.add(CompoundTag().apply {
                putUUID("entity", record.entityId)
                putUUID("owner", record.ownerId)
                putString("dimension", record.dimension.location().toString())
                putLong("pos", record.pos.asLong())
                putBoolean("beehemoth", record.beehemoth)
                putInt("tier", record.tier)
                record.returnDimension?.let { putString("return_dimension", it.location().toString()) }
                record.returnPos?.let { putLong("return_pos", it.asLong()) }
            })
        }
        tag.put("companions", list)
        return tag
    }

    companion object {
        private const val DATA_NAME = "better_dimension_fonts_companions"

        fun get(server: MinecraftServer): CompanionSavedData =
            server.overworld().dataStorage.computeIfAbsent(::load, ::CompanionSavedData, DATA_NAME)

        fun load(tag: CompoundTag): CompanionSavedData {
            require(tag.getInt("schema") == 1) { "Unsupported Font companion data schema" }
            val records = linkedMapOf<UUID, CompanionRecord>()
            val list = tag.getList("companions", Tag.TAG_COMPOUND.toInt())
            for (index in 0 until list.size) {
                val entry = list.getCompound(index)
                if (!entry.hasUUID("entity") || !entry.hasUUID("owner")) continue
                val dimension = ResourceLocation.tryParse(entry.getString("dimension")) ?: continue
                val record = CompanionRecord(
                    entry.getUUID("entity"), entry.getUUID("owner"),
                    ResourceKey.create(Registries.DIMENSION, dimension),
                    BlockPos.of(entry.getLong("pos")), entry.getBoolean("beehemoth"),
                    entry.getInt("tier"),
                    ResourceLocation.tryParse(entry.getString("return_dimension"))?.let {
                        ResourceKey.create(Registries.DIMENSION, it)
                    },
                    if (entry.contains("return_pos", Tag.TAG_LONG.toInt())) BlockPos.of(entry.getLong("return_pos")) else null
                )
                records[record.entityId] = record
            }
            return CompanionSavedData(records)
        }
    }
}
