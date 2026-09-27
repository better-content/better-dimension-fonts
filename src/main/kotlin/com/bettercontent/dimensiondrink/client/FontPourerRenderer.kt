package com.bettercontent.dimensiondrink.client

import com.bettercontent.dimensiondrink.content.FontPourerBlockEntity
import com.bettercontent.dimensiondrink.registry.ModLibationFluids
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.ResourceLocation

/** A continuous falling libation is visible through the air gap during an expedition. */
class FontPourerRenderer(@Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<FontPourerBlockEntity> {
    override fun render(entity: FontPourerBlockEntity, partialTick: Float, poseStack: PoseStack,
                        buffers: MultiBufferSource, packedLight: Int, packedOverlay: Int) {
        if (!entity.isPouring()) return
        val color = when (ModLibationFluids.definitionFor(entity.fluidHandler().getFluidInTank(0).fluid)) {
            "nether" -> intArrayOf(230, 95, 45)
            "aether" -> intArrayOf(120, 225, 245)
            "bumblezone" -> intArrayOf(245, 188, 52)
            "ratlantis" -> intArrayOf(130, 215, 160)
            else -> intArrayOf(180, 220, 225)
        }
        val sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
            .apply(ResourceLocation("minecraft", "block/water_still"))
        val consumer = buffers.getBuffer(RenderType.translucent())
        val pose = poseStack.last()
        val spread = 0.08f
        fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
            consumer.vertex(pose.pose(), x, y, z).color(color[0], color[1], color[2], 205)
                .uv(sprite.getU(u.toDouble()), sprite.getV(v.toDouble()))
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0f, 1f, 0f).endVertex()
        }
        fun plane(alongX: Boolean) {
            val x0 = if (alongX) 0.5f - spread else 0.5f
            val x1 = if (alongX) 0.5f + spread else 0.5f
            val z0 = if (alongX) 0.5f else 0.5f - spread
            val z1 = if (alongX) 0.5f else 0.5f + spread
            vertex(x0, -1.88f, z0, 0f, 16f)
            vertex(x1, -1.88f, z1, 16f, 16f)
            vertex(x1, 0.25f, z1, 16f, 0f)
            vertex(x0, 0.25f, z0, 0f, 0f)
        }
        plane(true)
        plane(false)
    }

    override fun shouldRenderOffScreen(entity: FontPourerBlockEntity): Boolean = true
}
