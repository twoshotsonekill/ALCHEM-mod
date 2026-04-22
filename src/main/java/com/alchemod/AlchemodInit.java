package com.alchemod;

import com.alchemod.block.BuilderBlock;
import com.alchemod.block.BuilderBlockEntity;
import com.alchemod.block.CreatorBlock;
import com.alchemod.block.CreatorBlockEntity;
import com.alchemod.block.ForgeBlock;
import com.alchemod.block.ForgeBlockEntity;
import com.alchemod.creator.DynamicItemRegistry;
import com.alchemod.screen.BuilderScreenHandler;
import com.alchemod.screen.CreatorScreenHandler;
import com.alchemod.screen.ForgeScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AlchemodInit implements ModInitializer {

    public static final String MOD_ID = "alchemod";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    // Populated in onInitialize after FabricLoader is ready
    public static String OPENROUTER_KEY = "";

    // ── Blocks & Items (initialized in onInitialize to avoid static init issues) ──

    public static Block FORGE_BLOCK;
    public static Item FORGE_ITEM;
    public static Block CREATOR_BLOCK;
    public static Item CREATOR_ITEM;
    public static Block BUILDER_BLOCK;
    public static Item BUILDER_ITEM;

    // ── Registry handles ──────────────────────────────────────────────────────

    public static BlockEntityType<ForgeBlockEntity>       FORGE_BE_TYPE;
    public static BlockEntityType<CreatorBlockEntity>     CREATOR_BE_TYPE;
    public static BlockEntityType<BuilderBlockEntity>     BUILDER_BE_TYPE;
    public static ScreenHandlerType<ForgeScreenHandler>   FORGE_HANDLER;
    public static ScreenHandlerType<CreatorScreenHandler> CREATOR_HANDLER;
    public static ScreenHandlerType<BuilderScreenHandler> BUILDER_HANDLER;

    // ── Entry point ───────────────────────────────────────────────────────────

    @Override
    public void onInitialize() {
        OPENROUTER_KEY = loadApiKey();
        LOG.info("Alchemod initialising — API key: {}", !OPENROUTER_KEY.isBlank() ? "SET" : "MISSING");

        // Register blocks and items (delayed from static init to avoid registry issues)
        FORGE_BLOCK = Registry.register(
                Registries.BLOCK, Identifier.of(MOD_ID, "alchemical_forge"),
                new ForgeBlock(AbstractBlock.Settings.create()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "alchemical_forge")))
                        .mapColor(MapColor.PURPLE).strength(4f, 8f).requiresTool()
                        .sounds(BlockSoundGroup.METAL).luminance(s -> 3)));

        FORGE_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "alchemical_forge"),
                new BlockItem(FORGE_BLOCK, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "alchemical_forge")))));

        CREATOR_BLOCK = Registry.register(
                Registries.BLOCK, Identifier.of(MOD_ID, "item_creator"),
                new CreatorBlock(AbstractBlock.Settings.create()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "item_creator")))
                        .mapColor(MapColor.GOLD).strength(4f, 8f).requiresTool()
                        .sounds(BlockSoundGroup.METAL).luminance(s -> 5)));

        CREATOR_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "item_creator"),
                new BlockItem(CREATOR_BLOCK, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "item_creator")))));

        BUILDER_BLOCK = Registry.register(
                Registries.BLOCK, Identifier.of(MOD_ID, "build_creator"),
                new BuilderBlock(AbstractBlock.Settings.create()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "build_creator")))
                        .mapColor(MapColor.BROWN).strength(4f, 8f).requiresTool()
                        .sounds(BlockSoundGroup.METAL).luminance(s -> 4)));

        BUILDER_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "build_creator"),
                new BlockItem(BUILDER_BLOCK, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "build_creator")))));

        DynamicItemRegistry.register();

        FORGE_BE_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "alchemical_forge"),
                FabricBlockEntityTypeBuilder.create(ForgeBlockEntity::new, FORGE_BLOCK).build());

        CREATOR_BE_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "item_creator"),
                FabricBlockEntityTypeBuilder.create(CreatorBlockEntity::new, CREATOR_BLOCK).build());

        BUILDER_BE_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "build_creator"),
                FabricBlockEntityTypeBuilder.create(BuilderBlockEntity::new, BUILDER_BLOCK).build());

        FORGE_HANDLER = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "alchemical_forge"),
                new ScreenHandlerType<>(ForgeScreenHandler::new, FeatureSet.empty()));

        CREATOR_HANDLER = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "item_creator"),
                new ScreenHandlerType<>(CreatorScreenHandler::new, FeatureSet.empty()));

        BUILDER_HANDLER = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "build_creator"),
                new ScreenHandlerType<>(BuilderScreenHandler::new, FeatureSet.empty()));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e -> {
            e.add(FORGE_ITEM);
            e.add(CREATOR_ITEM);
            e.add(BUILDER_ITEM);
        });
    }

    // ── API key loading ───────────────────────────────────────────────────────
    //
    // Priority (highest first):
    //   1. OPENROUTER_API_KEY  — OS-level environment variable
    //   2. config/alchemod.properties  — file in the Minecraft config directory
    //
    // The properties file is created automatically on first run with instructions.

    private static String loadApiKey() {
        // 1. OS environment variable (set globally so all Minecraft instances share it)
        String env = System.getenv("OPENROUTER_API_KEY");
        if (env != null && !env.isBlank()) {
            LOG.info("[Alchemod] API key loaded from environment variable.");
            return env.trim();
        }

        // 2. Config file: <minecraft-dir>/config/alchemod.properties
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("alchemod.properties");
        if (!Files.exists(configPath)) {
            writeDefaultConfig(configPath);
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(configPath)) {
            props.load(in);
        } catch (IOException e) {
            LOG.error("[Alchemod] Failed to read config: {}", e.getMessage());
            return "";
        }

        String key = props.getProperty("openrouter_api_key", "").trim();
        if (!key.isBlank() && !key.startsWith("YOUR_")) {
            LOG.info("[Alchemod] API key loaded from config/alchemod.properties.");
            return key;
        }

        LOG.warn("[Alchemod] No API key configured. " +
                "Set the OPENROUTER_API_KEY environment variable, " +
                "or paste your key into config/alchemod.properties.");
        return "";
    }

    private static void writeDefaultConfig(Path path) {
        String template = """
                # Alchemod — API Key Configuration
                # ==================================
                #
                # Get a free key at https://openrouter.ai
                #
                # Option A (recommended) — set a global OS environment variable so every
                # Minecraft instance and launcher shares the same key automatically:
                #
                #   Windows : System Properties → Advanced → Environment Variables → New
                #             Variable name : OPENROUTER_API_KEY
                #             Variable value: sk-or-v1-xxxxxxxx...
                #
                #   macOS   : echo 'export OPENROUTER_API_KEY=sk-or-v1-...' >> ~/.zshrc
                #             (restart your terminal / launcher afterwards)
                #
                #   Linux   : echo 'export OPENROUTER_API_KEY=sk-or-v1-...' >> ~/.profile
                #
                # Option B — paste your key directly below (used only if env var is absent):
                #
                openrouter_api_key=YOUR_KEY_HERE
                """;
        try (OutputStream out = Files.newOutputStream(path)) {
            out.write(template.getBytes(StandardCharsets.UTF_8));
            LOG.info("[Alchemod] Created default config at {}", path);
        } catch (IOException e) {
            LOG.error("[Alchemod] Could not create config file: {}", e.getMessage());
        }
    }
}
