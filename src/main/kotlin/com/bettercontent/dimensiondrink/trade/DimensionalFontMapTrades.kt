package com.bettercontent.dimensiondrink.trade

import com.bettercontent.dimensiondrink.MOD_ID
import com.bettercontent.dimensiondrink.data.ObeliskDataManager
import com.bettercontent.dimensiondrink.data.ObeliskDefinition
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.npc.AbstractVillager
import net.minecraft.world.entity.npc.VillagerTrades
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.inventory.MerchantMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.MapItem
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.level.saveddata.maps.MapDecoration
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

object DimensionalFontMapTrades {
    private const val SOLD_TYPES_TAG = "dimension_drink:font_map_sold_types"
    private const val ALLOWED_TYPES_TAG = "dimension_drink:font_map_allowed_types"

    /** Stable caller API for continuing an authored seller's existing map rotation. */
    @JvmStatic
    fun soldDefinitionIds(sellerData: CompoundTag): Set<String> = readSoldTypes(sellerData)

    /** The economy sets this whenever an authored seller is assigned an aspect theme. */
    @JvmStatic
    fun setSellerDefinitionIds(sellerData: CompoundTag, allowedTypes: Set<String>) {
        writeStringSet(sellerData, ALLOWED_TYPES_TAG, allowedTypes)
        writeSoldTypes(sellerData, readSoldTypes(sellerData).intersect(allowedTypes))
    }

    /** Stable JVM entry point used by the pack's wandering-trader integration. */
    @JvmStatic
    fun wanderingTraderListing(villagerXp: Int): VillagerTrades.ItemListing =
        DimensionalFontMapListing(villagerXp)

    /**
     * Creates the next map offer for an authored seller.
     *
     * The caller supplies the payment item because Dimension Drink does not own the
     * pack's economy. The destination is selected only from [FontLocationSavedData],
     * so this method never searches for or generates a chunk. [excludedTypes] should
     * contain the seller's already-sold definition ids for the current seller cycle;
     * sale recording and cycle advancement remain the caller's responsibility through
     * [onTradeCompleted].
     */
    @JvmStatic
    fun authoredSellerOffer(
        level: ServerLevel,
        origin: BlockPos,
        villagerXp: Int,
        currency: Item,
        excludedTypes: Set<String> = emptySet(),
        allowedTypes: Set<String> = DimensionalFontMapListing.enabledDefinitionIds()
    ): MerchantOffer? = DimensionalFontMapListing(villagerXp)
        .nextOffer(level, origin, excludedTypes, currency, allowedTypes)

    @SubscribeEvent
    fun onTradeCompleted(event: TradeWithVillagerEvent) {
        val offer = event.merchantOffer
        val soldDefinitionId = offer.result.tag?.getString(DimensionalFontMapListing.DEFINITION_TAG)
            ?.takeIf(String::isNotBlank)
            ?: return
        val villager = event.abstractVillager
        val level = villager.level() as? ServerLevel ?: return
        FontLocationSavedData.get(level.server).recordMapSale(soldDefinitionId)
        val configured = readStringSet(villager.persistentData, ALLOWED_TYPES_TAG)
        val eligibleTypes = if (configured.isEmpty()) DimensionalFontMapListing.enabledDefinitionIds()
            else configured.intersect(DimensionalFontMapListing.enabledDefinitionIds())
        val soldTypes = advanceSoldTypes(readSoldTypes(villager.persistentData), soldDefinitionId, eligibleTypes)
        writeSoldTypes(villager.persistentData, soldTypes)

        val nextMap = DimensionalFontMapListing(0).nextMap(level, villager.blockPosition(), soldTypes, eligibleTypes)
        if (nextMap == null) {
            offer.setToOutOfStock()
        } else {
            replaceOfferResult(offer, nextMap)
        }

        val player = event.entity as? ServerPlayer ?: return
        val menu = player.containerMenu as? MerchantMenu ?: return
        player.connection.send(
            ClientboundMerchantOffersPacket(
                menu.containerId,
                villager.offers,
                if (villager is Villager) villager.villagerData.level else 1,
                if (villager is Villager) villager.villagerXp else 0,
                villager.showProgressBar(),
                villager is Villager && villager.canRestock()
            )
        )
    }

    internal fun advanceSoldTypes(
        previous: Set<String>,
        soldDefinitionId: String,
        eligibleTypes: Set<String>
    ): Set<String> {
        if (eligibleTypes.isEmpty()) return emptySet()
        val updated = previous.filterTo(linkedSetOf()) { it in eligibleTypes }
        if (soldDefinitionId in eligibleTypes) updated += soldDefinitionId
        return if (updated.containsAll(eligibleTypes)) emptySet() else updated
    }

    internal fun replaceOfferResult(offer: MerchantOffer, nextMap: ItemStack) {
        offer.result.setTag(nextMap.tag?.copy())
        offer.result.count = nextMap.count
    }

    internal fun readSoldTypes(tag: CompoundTag): Set<String> = readStringSet(tag, SOLD_TYPES_TAG)

    private fun readStringSet(tag: CompoundTag, key: String): Set<String> {
        val values = tag.getList(key, Tag.TAG_STRING.toInt())
        return (0 until values.size).mapTo(linkedSetOf(), values::getString)
    }

    private fun writeSoldTypes(tag: CompoundTag, soldTypes: Set<String>) =
        writeStringSet(tag, SOLD_TYPES_TAG, soldTypes)

    private fun writeStringSet(tag: CompoundTag, key: String, valuesToWrite: Set<String>) {
        val values = ListTag()
        valuesToWrite.sorted().forEach { values.add(StringTag.valueOf(it)) }
        tag.put(key, values)
    }
}

class DimensionalFontMapListing(
    private val villagerXp: Int
) : VillagerTrades.ItemListing {
    override fun getOffer(trader: Entity, random: RandomSource): MerchantOffer? {
        // The seven themed spirit traders own the entire wandering-trader surface.
        // Retain this listing type as a binary-compatible no-op for older integrations.
        return null
    }

    internal fun nextMap(
        level: ServerLevel,
        origin: BlockPos,
        excludedTypes: Set<String>,
        allowedTypes: Set<String> = enabledDefinitionIds()
    ): ItemStack? {
        val eligibleTypes = enabledDefinitionIds().intersect(allowedTypes)
        if (eligibleTypes.isEmpty()) return null
        val destination = FontLocationSavedData.get(level.server)
            .nearest(level, origin, eligibleTypes, excludedTypes) ?: return null
        val definition = ObeliskDataManager.getObelisk(destination.definitionId) ?: return null
        return createMap(level, destination.pos, definition)
    }

    internal fun nextOffer(
        level: ServerLevel,
        origin: BlockPos,
        excludedTypes: Set<String>,
        currency: Item,
        allowedTypes: Set<String>
    ): MerchantOffer? = nextMap(level, origin, excludedTypes, allowedTypes)?.let { map ->
        createOffer(map, villagerXp, currency)
    }

    companion object {
        const val DEFINITION_TAG = "dimension_drink:font_definition_id"
        const val COST = 8
        const val MAX_USES = 8

        internal fun createOffer(
            level: ServerLevel,
            center: BlockPos,
            definition: ObeliskDefinition,
            villagerXp: Int,
            currency: Item
        ): MerchantOffer {
            return createOffer(createMap(level, center, definition), villagerXp, currency)
        }

        internal fun createMap(
            level: ServerLevel,
            center: BlockPos,
            definition: ObeliskDefinition
        ): ItemStack {
            val map = MapItem.create(level, center.x, center.z, 2.toByte(), true, true)
            MapItemSavedData.addTargetDecoration(map, center, "+", MapDecoration.Type.TARGET_X)

            val displayName = Component.literal(definition.displayName)
            map.setHoverName(Component.translatable("item.dimension_drink.dimensional_font_map", displayName))
            val lore = ListTag()
            lore.add(
                StringTag.valueOf(
                    Component.Serializer.toJson(
                        Component.translatable("item.dimension_drink.dimensional_font_map.destination", displayName)
                    )
                )
            )
            if (definition.salienceAspects.isNotEmpty()) {
                lore.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.translatable(
                        "item.dimension_drink.dimensional_font_map.aspects",
                        definition.salienceAspects.joinToString(" · ") { it.replaceFirstChar(Char::uppercase) }
                    )
                )))
            }
            map.getOrCreateTagElement("display").put("Lore", lore)
            map.orCreateTag.putString(DEFINITION_TAG, definition.id)
            return map
        }

        /** Creates a map offer using exactly the currency item supplied by the seller. */
        @JvmStatic
        fun createOffer(map: ItemStack, villagerXp: Int, currency: Item): MerchantOffer {
            return MerchantOffer(ItemStack(currency, COST), map, MAX_USES, villagerXp, 0.0f)
        }

        internal fun enabledDefinitionIds(): Set<String> = com.bettercontent.dimensiondrink.worldgen.FontSelector
            .eligible(ObeliskDataManager.enabledDimensionDrinks())
            .mapTo(linkedSetOf(), ObeliskDefinition::id)

    }
}
