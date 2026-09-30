package com.bettercontent.dimensiondrink.runtime.player

import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.OwnableEntity
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.EntityJoinLevelEvent
import net.minecraftforge.event.entity.EntityLeaveLevelEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Font-only travel for marked companions and all owned Bumblezone Beehemoths. */
object FontCompanions {
    private val logger = LogUtils.getLogger()
    private val beehemothId = ResourceLocation("the_bumblezone", "beehemoth")
    private val bumblezoneId = ResourceLocation("the_bumblezone", "the_bumblezone")
    private val bosses = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation("forge", "bosses"))
    private val inFlight = mutableSetOf<UUID>()
    private var retryTick = 0L
    private var retryCursor = 0

    fun shouldPreserveOnRunClose(mob: Mob): Boolean {
        val level = mob.level() as? ServerLevel ?: return false
        val record = CompanionSavedData.get(level.server).get(mob.uuid)
        return record?.tier?.let { it >= 0 } == true ||
            (isBeehemoth(mob) && (mob as? TamableAnimal)?.ownerUUID != null)
    }

    fun mark(player: net.minecraft.world.entity.player.Player, target: LivingEntity, tier: Int): Boolean {
        val serverPlayer = player as? ServerPlayer ?: return false
        val mob = target as? Mob ?: return reject(serverPlayer, "Only living mobs can wear a collar.")
        if (tier !in 0..2 || mob is EnderDragon || mob is WitherBoss || mob.type.`is`(bosses)) {
            return reject(serverPlayer, "A boss cannot wear a dimensional collar.")
        }
        if (mob is OwnableEntity && mob.ownerUUID != null && mob.ownerUUID != player.uuid) {
            return reject(serverPlayer, "This companion belongs to another player.")
        }
        val data = CompanionSavedData.get(serverPlayer.server)
        val present = data.get(mob.uuid)
        if (present != null && present.ownerId != player.uuid) {
            return reject(serverPlayer, "This companion is already marked by another player.")
        }
        data.all().filter { it.ownerId == player.uuid && it.tier == tier && it.entityId != mob.uuid }
            .forEach { previous ->
                if (previous.beehemoth) data.put(previous.copy(tier = -1))
                else data.remove(previous.entityId)
            }
        data.put(CompanionRecord(
            mob.uuid, player.uuid, serverPlayer.serverLevel().dimension(), mob.blockPosition().immutable(),
            isBeehemoth(mob), tier
        ))
        player.displayClientMessage(Component.literal("Companion marked for Font travel."), true)
        return true
    }

    fun onEnter(player: ServerPlayer, source: ServerLevel, sourcePos: Vec3) {
        val data = CompanionSavedData.get(player.server)
        val target = player.serverLevel()
        val nearby = AABB.ofSize(sourcePos, 16.0, 16.0, 16.0)
        val eligible = source.getEntitiesOfClass(Mob::class.java, nearby) { mob ->
            val record = data.get(mob.uuid)
            record != null && record.ownerId == player.uuid && record.tier >= 0 &&
                mob.distanceToSqr(sourcePos) <= 64.0
        }
        eligible.forEachIndexed { index, mob -> transfer(mob, target, player.blockPosition(), index, data) }
    }

    fun onReturn(player: ServerPlayer, source: ServerLevel) {
        val data = CompanionSavedData.get(player.server)
        source.allEntities.filterIsInstance<Mob>()
            .filter { isBeehemoth(it) || data.get(it.uuid) != null }
            .forEach { track(it, source) }
        val origin = player.serverLevel()
        val destination = player.blockPosition().immutable()
        data.all().filter { record ->
            record.ownerId == player.uuid && record.dimension == source.dimension() &&
                (record.tier >= 0 || (record.beehemoth && source.dimension().location() == bumblezoneId))
        }.forEach { record -> data.put(record.copy(returnDimension = origin.dimension(), returnPos = destination)) }
        processReturns(player.server)
    }

    @SubscribeEvent
    fun onJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity as? Mob ?: return
        val level = event.level as? ServerLevel ?: return
        if (entity.uuid in inFlight) return
        track(entity, level)
    }

    @SubscribeEvent
    fun onLeave(event: EntityLeaveLevelEvent) {
        val entity = event.entity as? Mob ?: return
        val level = event.level as? ServerLevel ?: return
        if (entity.uuid in inFlight || !entity.isAlive) return
        track(entity, level)
    }

    @SubscribeEvent
    fun onLivingTick(event: LivingEvent.LivingTickEvent) {
        val mob = event.entity as? Mob ?: return
        val level = mob.level() as? ServerLevel ?: return
        if (mob.tickCount % 20 != 0) return
        track(mob, level)
        val tier = CompanionSavedData.get(level.server).get(mob.uuid)?.tier ?: -1
        if (tier >= 0) {
            val color = when (tier) {
                0 -> Vector3f(0.83f, 0.68f, 0.42f)
                1 -> Vector3f(0.76f, 0.82f, 0.88f)
                else -> Vector3f(0.45f, 0.85f, 1.0f)
            }
            level.sendParticles(DustParticleOptions(color, 1.0f), mob.x, mob.y + mob.bbHeight + 0.3,
                mob.z, 2, 0.2, 0.05, 0.2, 0.0)
        }
    }

    @SubscribeEvent
    fun onDeath(event: LivingDeathEvent) {
        val level = event.entity.level() as? ServerLevel ?: return
        CompanionSavedData.get(level.server).remove(event.entity.uuid)
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        if (++retryTick % 20L == 0L) processReturns(event.server)
    }

    private fun track(entity: Mob, level: ServerLevel) {
        val data = CompanionSavedData.get(level.server)
        val existing = data.get(entity.uuid)
        val bee = isBeehemoth(entity)
        val nativeOwner = (entity as? TamableAnimal)?.ownerUUID
        if (!bee && existing == null) return
        if (existing == null && nativeOwner == null) return
        if (existing != null && !bee && existing.tier < 0) {
            data.remove(entity.uuid)
            return
        }
        val owner = existing?.ownerId ?: nativeOwner ?: return
        // A changed native owner invalidates the prior player's mark.
        if (nativeOwner != null && existing != null && nativeOwner != existing.ownerId) {
            data.put(CompanionRecord(entity.uuid, nativeOwner, level.dimension(), entity.blockPosition(), bee))
            return
        }
        data.put(CompanionRecord(entity.uuid, owner, level.dimension(), entity.blockPosition().immutable(),
            bee, existing?.tier ?: -1, existing?.returnDimension, existing?.returnPos))
    }

    private fun processReturns(server: net.minecraft.server.MinecraftServer) {
        val data = CompanionSavedData.get(server)
        val pending = data.all().filter { it.returnDimension != null && it.returnPos != null }
        if (pending.isEmpty()) return
        val start = retryCursor % pending.size
        val batchSize = minOf(32, pending.size)
        retryCursor = (start + batchSize) % pending.size
        (0 until batchSize).forEach { batchIndex ->
                val index = (start + batchIndex) % pending.size
                val record = pending[index]
                val source = server.getLevel(record.dimension) ?: return@forEach
                val target = server.getLevel(record.returnDimension) ?: return@forEach
                source.getChunk(record.pos.x shr 4, record.pos.z shr 4)
                val entity = source.getEntity(record.entityId) as? Mob ?: return@forEach
                if (entity.isAlive && ownerOf(entity, record) == record.ownerId) {
                    transfer(entity, target, record.returnPos!!, index, data)
                } else {
                    data.remove(record.entityId)
                }
            }
    }

    private fun ownerOf(entity: Mob, record: CompanionRecord): UUID? =
        (entity as? TamableAnimal)?.ownerUUID ?: record.ownerId.takeIf { record.tier >= 0 }

    private fun transfer(entity: Mob, target: ServerLevel, around: BlockPos, index: Int, data: CompanionSavedData): Boolean {
        val source = entity.level() as? ServerLevel ?: return false
        if (source.dimension() == target.dimension()) return false
        val copy = entity.type.create(target) as? Mob ?: return false
        val tag = CompoundTag()
        entity.saveWithoutId(tag)
        copy.load(tag)
        val space = (0 until 32).firstOrNull { attempt ->
            val angle = (index + attempt) * 2.399963229728653
            val radius = 3.0 + sqrt((index + attempt).toDouble()) * 1.5
            val x = around.x + 0.5 + cos(angle) * radius
            val z = around.z + 0.5 + sin(angle) * radius
            val y = around.y + 1.0 + (attempt % 4)
            copy.moveTo(x, y, z, entity.yRot, entity.xRot)
            target.noCollision(copy)
        }
        if (space == null) return false
        inFlight += entity.uuid
        try {
            if (!target.addFreshEntity(copy)) return false
            entity.discard()
            val record = data.get(entity.uuid) ?: return true
            data.put(record.copy(dimension = target.dimension(), pos = copy.blockPosition().immutable(),
                returnDimension = null, returnPos = null))
            return true
        } catch (failure: Exception) {
            logger.error("Could not transport Font companion {}", entity.uuid, failure)
            return false
        } finally {
            inFlight -= entity.uuid
        }
    }

    private fun isBeehemoth(entity: Entity): Boolean = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type) == beehemothId

    private fun reject(player: ServerPlayer, message: String): Boolean {
        player.displayClientMessage(Component.literal(message), true)
        return false
    }
}
