package com.bettercontent.dimensiondrink.visualharness;

import com.bettercontent.dimensiondrink.worldgen.structure.DimensionalFontSiteGenerator;
import com.bettercontent.dimensiondrink.worldgen.structure.DimensionalFontStructurePiece;
import com.bettercontent.dimensiondrink.data.ObeliskDataManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Disposable server scene builder for direct screenshots of the production structure generator. */
@Mod(FontVisualHarness.MOD_ID)
public final class FontVisualHarness {
    public static final String MOD_ID = "dimension_drink_visual_harness";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(MOD_ID, "capture"))
            .networkProtocolVersion(() -> "1")
            .clientAcceptedVersions("1"::equals).serverAcceptedVersions("1"::equals).simpleChannel();
    private static final List<String> FONTS = List.of("nether", "aether", "bumblezone", "ratlantis");
    private static BlockPos center;

    public FontVisualHarness() {
        CHANNEL.messageBuilder(CapturePacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CapturePacket::encode).decoder(CapturePacket::decode)
                .consumerMainThread(CapturePacket::handle).add();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void registerCommands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fontvisual").requires(source -> source.hasPermission(2))
                .then(Commands.literal("prepare").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("font", StringArgumentType.word())
                                .then(Commands.argument("biome", StringArgumentType.word())
                                        .executes(context -> prepare(EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "font"),
                                                StringArgumentType.getString(context, "biome")))))))
                .then(Commands.literal("view").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("angle", StringArgumentType.word())
                                .executes(context -> view(EntityArgument.getPlayer(context, "player"),
                                        StringArgumentType.getString(context, "angle"))))))
                .then(Commands.literal("capture").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> capture(EntityArgument.getPlayer(context, "player"),
                                        StringArgumentType.getString(context, "name")))))));
    }

    private static int prepare(final ServerPlayer player, final String font, final String biome) {
        if (!FONTS.contains(font) || !(biome.equals("forest") || biome.equals("desert"))) {
            player.sendSystemMessage(Component.literal("Use nether/aether/bumblezone/ratlantis and forest/desert."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        int x = Math.floorDiv(player.blockPosition().getX() + 80, 16) * 16 + 8;
        int z = Math.floorDiv(player.blockPosition().getZ() - 80, 16) * 16 + 8;
        int y = 80;
        center = new BlockPos(x, y, z);
        if (ObeliskDataManager.INSTANCE.getObelisk(font) == null) {
            player.sendSystemMessage(Component.literal("Font definition unavailable in the visual runtime: " + font));
            return 0;
        }
        for (int dx = -33; dx <= 33; dx++) {
            for (int dz = -33; dz <= 33; dz++) {
                BlockPos floor = center.offset(dx, 0, dz);
                level.setBlock(floor, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int height = 1; height <= 13; height++)
                    level.setBlock(floor.above(height), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        String fillBiome = "fillbiome " + (x - 8) + " " + (y - 4) + " " + (z - 8) + " "
                + (x + 8) + " " + (y + 8) + " " + (z + 8) + " minecraft:" + biome;
        int changed = level.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4), fillBiome);
        System.out.println("FONT_VISUAL biome=" + level.getBiome(center).unwrapKey().orElseThrow().location()
                + " changed=" + changed + " center=" + center);

        DimensionalFontStructurePiece piece = new DimensionalFontStructurePiece(center,
                0x464f4e5456495355L, font, 22500.0, DimensionalFontSiteGenerator.LAYOUT_VERSION);
        ChunkPos centerChunk = new ChunkPos(center);
        for (int chunkX = centerChunk.x - 2; chunkX <= centerChunk.x + 2; chunkX++) {
            for (int chunkZ = centerChunk.z - 2; chunkZ <= centerChunk.z + 2; chunkZ++) {
                ChunkPos slice = new ChunkPos(chunkX, chunkZ);
                BoundingBox box = new BoundingBox(slice.getMinBlockX(), level.getMinBuildHeight(), slice.getMinBlockZ(),
                        slice.getMaxBlockX(), level.getMaxBuildHeight() - 1, slice.getMaxBlockZ());
                piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                        RandomSource.create(0x464f4e5456495355L), box, slice, BlockPos.ZERO);
            }
        }
        view(player, "overview");
        player.sendSystemMessage(Component.literal("Font visual: " + font + " on " + biome + " at " + center));
        return 1;
    }

    private static int view(final ServerPlayer player, final String angle) {
        if (center == null) return 0;
        double x; double y; double z; float yaw; float pitch;
        switch (angle) {
            case "detail" -> {
                x = center.getX() + 7.5; y = center.getY() + 8.5; z = center.getZ() + 13.5;
                yaw = 150.0f; pitch = 22.0f;
            }
            case "overhead" -> {
                x = center.getX() + 0.5; y = center.getY() + 24.5; z = center.getZ() + 13.5;
                yaw = 180.0f; pitch = 59.0f;
            }
            case "overview" -> {
                x = center.getX() + 0.5; y = center.getY() + 17.5; z = center.getZ() + 29.5;
                yaw = 180.0f; pitch = 28.0f;
            }
            default -> { return 0; }
        }
        player.connection.teleport(x, y, z, yaw, pitch);
        player.setNoGravity(true);
        return 1;
    }

    private static int capture(final ServerPlayer player, final String name) {
        if (!name.matches("[a-z0-9_-]{1,48}")) return 0;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CapturePacket(name));
        return 1;
    }

    private record CapturePacket(String name) {
        private static void encode(final CapturePacket packet, final FriendlyByteBuf buffer) { buffer.writeUtf(packet.name(), 64); }
        private static CapturePacket decode(final FriendlyByteBuf buffer) { return new CapturePacket(buffer.readUtf(64)); }
        private static void handle(final CapturePacket packet,
                                   final java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            FontVisualScreenshot.request(packet.name());
        }
    }
}
