package com.bettercontent.betterdimensionfonts.visualharness;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Reads the actual rendered framebuffer after scene and camera updates settle. */
public final class FontVisualScreenshot {
    private static String pending;
    private static int settleTicks;
    private static boolean installed;

    private FontVisualScreenshot() { }

    static void request(final String name) {
        if (name.equals("__shutdown__")) {
            // Non-shipping control packet: let Minecraft close GL/audio/network before Xvfb.
            System.out.println("FONT_VISUAL shutdown_requested");
            Minecraft.getInstance().stop();
            return;
        }
        if (!installed) {
            MinecraftForge.EVENT_BUS.register(FontVisualScreenshot.class);
            installed = true;
        }
        Minecraft.getInstance().options.hideGui = true;
        pending = name + ".png";
        settleTicks = 0;
    }

    @SubscribeEvent
    public static void tick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || !minecraft.level.hasChunkAt(minecraft.player.blockPosition()) || ++settleTicks < 35) return;
        String fileName = pending;
        pending = null;
        var snapshot = com.bettercontent.celestial.api.CelestialEventsApi.clientSnapshot();
        var moonResource = com.bettercontent.celestial.client.MoonPresentation.texture(
                new net.minecraft.resources.ResourceLocation("minecraft", "textures/environment/moon_phases.png"));
        String resourceHash;
        try (var stream = minecraft.getResourceManager().getResource(moonResource).orElseThrow().open()) {
            resourceHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Moon resource unavailable: " + moonResource, failure);
        }
        com.bettercontent.betterdimensionfonts.content.ObeliskBlockEntity font = null, seal = null;
        var playerPos = minecraft.player.blockPosition();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            var chunk = minecraft.level.getChunkSource().getChunk((playerPos.getX() >> 4) + dx,
                    (playerPos.getZ() >> 4) + dz, net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
            if (chunk == null) continue;
            for (var entity : chunk.getBlockEntities().values()) {
                if (!(entity instanceof com.bettercontent.betterdimensionfonts.content.ObeliskBlockEntity candidate)
                        || candidate.getBlockPos().distSqr(playerPos) > 32 * 32) continue;
                if (candidate.getBlockState().is(com.bettercontent.betterdimensionfonts.registry.ModBlocks.INSTANCE.getOBELISK().get())) {
                    if (font == null || candidate.getBlockPos().distSqr(playerPos) < font.getBlockPos().distSqr(playerPos)) font = candidate;
                } else if (candidate.getBlockState().is(com.bettercontent.betterdimensionfonts.registry.ModBlocks.INSTANCE.getRETURN_FONT().get())) seal = candidate;
            }
        }
        System.out.println("CELESTIAL_VISUAL capture=" + fileName + " player=" + minecraft.getUser().getName()
                + " event=" + snapshot.event() + " active=" + snapshot.active() + " occurrence=" + snapshot.occurrenceId()
                + " remaining=" + snapshot.remainingNightTicks() + " dimension=" + minecraft.level.dimension().location()
                + " resource=" + moonResource + " resource_sha256=" + resourceHash
                + " font_charge=" + (font == null ? -1 : font.saveWithoutMetadata().getInt("charge_stored"))
                + " font_max=" + (font == null ? -1 : font.getMaxChargeStored())
                + " font_ready=" + (font != null && font.isReadyToOpen()) + " font_free=" + (font != null && font.isOpenWaysAvailable())
                + " return_seen=" + (seal != null) + " return_free=" + (seal != null && seal.isOpenWaysAvailable()));
        Screenshot.grab(minecraft.gameDirectory, fileName, minecraft.getMainRenderTarget(), message -> {
            Path output = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(fileName);
            if (!Files.isRegularFile(output)) throw new IllegalStateException("Font screenshot failed: " + message.getString());
            System.out.println("FONT_VISUAL captured " + output.toAbsolutePath());
        });
    }
}
