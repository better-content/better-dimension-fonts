package com.bettercontent.dimensiondrink.visualharness;

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
        if (minecraft.level == null || ++settleTicks < 35) return;
        String fileName = pending;
        pending = null;
        Screenshot.grab(minecraft.gameDirectory, fileName, minecraft.getMainRenderTarget(), message -> {
            Path output = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(fileName);
            if (!Files.isRegularFile(output)) throw new IllegalStateException("Font screenshot failed: " + message.getString());
            System.out.println("FONT_VISUAL captured " + output.toAbsolutePath());
        });
    }
}
