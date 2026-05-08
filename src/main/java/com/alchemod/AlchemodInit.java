package com.alchemod;

import com.alchemod.ai.AlchemodConfig;
import com.alchemod.block.AlchemicalGlassBlock;
import com.alchemod.block.ArcaneBricksBlock;
import com.alchemod.block.BuilderBlock;
import com.alchemod.block.BuilderBlockEntity;
import com.alchemod.block.CreatorBlock;
import com.alchemod.block.CreatorBlockEntity;
import com.alchemod.block.EtherCrystalBlock;
import com.alchemod.block.ForgeBlock;
import com.alchemod.block.ForgeBlockEntity;
import com.alchemod.block.GlowStoneBricksBlock;
import com.alchemod.block.ReinforcedObsidianBlock;
import com.alchemod.block.VoidStoneBlock;
import com.alchemod.creator.DynamicItemRegistry;
import com.alchemod.event.ItemAbilityEvents;
import com.alchemod.item.OddityItem;
import com.alchemod.network.BuilderPromptPayload;
import com.alchemod.network.ForgeNbtPayload;
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

public class AlchemodInit implements ModInitializer {

    public static final String MOD_ID = "alchemod";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static String OPENROUTER_KEY = "";
    public static AlchemodConfig CONFIG;

    public static Block FORGE_BLOCK;
    public static Item  FORGE_ITEM;
    public static Block CREATOR_BLOCK;
    public static Item  CREATOR_ITEM;
    public static Block BUILDER_BLOCK;
    public static Item  BUILDER_ITEM;

    // Decorative blocks
    public static Block ALCHEMICAL_GLASS_BLOCK;
    public static Item  ALCHEMICAL_GLASS_ITEM;
    public static Block REINFORCED_OBSIDIAN_BLOCK;
    public static Item  REINFORCED_OBSIDIAN_ITEM;
    public static Block GLOWSTONE_BRICKS_BLOCK;
    public static Item  GLOWSTONE_BRICKS_ITEM;
    public static Block ARCANE_BRICKS_BLOCK;
    public static Item  ARCANE_BRICKS_ITEM;
    public static Block VOID_STONE_BLOCK;
    public static Item  VOID_STONE_ITEM;
    public static Block ETHER_CRYSTAL_BLOCK;
    public static Item  ETHER_CRYSTAL_ITEM;

    /** Legacy NBT-driven item kept registered for existing Oddity stacks. */
    public static OddityItem ODDITY_ITEM_INSTANCE;

    public static BlockEntityType<ForgeBlockEntity>   FORGE_BE_TYPE;
    public static BlockEntityType<CreatorBlockEntity> CREATOR_BE_TYPE;
    public static BlockEntityType<BuilderBlockEntity> BUILDER_BE_TYPE;
    public static ScreenHandlerType<ForgeScreenHandler>   FORGE_HANDLER;
    public static ScreenHandlerType<CreatorScreenHandler> CREATOR_HANDLER;
    public static ScreenHandlerType<BuilderScreenHandler> BUILDER_HANDLER;

    @Override
    public void onInitialize() {
        CONFIG = AlchemodConfig.load(
                FabricLoader.getInstance().getConfigDir().resolve("alchemod.properties"), LOG);
        OPENROUTER_KEY = CONFIG.openRouterApiKey();
        LOG.info("Alchemod initialising — model: {} | API key: {}",
                CONFIG.builderModel(), !OPENROUTER_KEY.isBlank() ? "SET" : "MISSING");

        // ── Functional blocks ──────────────────────────────────────────────────
        FORGE_BLOCK = registerBlock("alchemical_forge",
                new ForgeBlock(AbstractBlock.Settings.create()
                        .registryKey(blockKey("alchemical_forge"))
                        .mapColor(MapColor.PURPLE).strength(4f, 8f).requiresTool()
                        .sounds(BlockSoundGroup.METAL).luminance(s -> 3)));
        FORGE_ITEM = registerBlockItem("alchemical_forge", FORGE_BLOCK);

        CREATOR_BLOCK = registerBlock("item_creator",
                new CreatorBlock(AbstractBlock.Settings.create()
                        .registryKey(blockKey("item_creator"))
                        .mapColor(MapColor.GOLD).strength(4f, 8f).requiresTool()
                        .sounds(BlockSoundGroup.METAL).luminance(s -> 5)));
        CREATOR_ITEM = registerBlockItem("item_creator", CREATOR_BLOCK);

        BUILDER_BLOCK = registerBlock("build_creator",
                new BuilderBlock(AbstractBlock.Settings.create()
                        .registryKey(blockKey("build_creator"))
                        .mapColor(MapColor.BROWN).strength(4f, 8f).requiresTool()
                        .sounds(BlockSoundGroup.METAL).luminance(s -> 4)));
        BUILDER_ITEM = registerBlockItem("build_creator", BUILDER_BLOCK);

        // ── Decorative blocks ──────────────────────────────────────────────────
        ALCHEMICAL_GLASS_BLOCK = registerBlock("alchemical_glass",
                new AlchemicalGlassBlock(AbstractBlock.Settings.create()
                        .registryKey(blockKey("alchemical_glass"))
                        .mapColor(MapColor.CYAN).strength(0.3f, 0.3f)
                        .sounds(BlockSoundGroup.GLASS).luminance(s -> 2)
                        .nonOpaque().allowsSpawning((st, w, p, e) -> false)));
        ALCHEMICAL_GLASS_ITEM = registerBlockItem("alchemical_glass", ALCHEMICAL_GLASS_BLOCK);

        REINFORCED_OBSIDIAN_BLOCK = registerBlock("reinforced_obsidian",
                new ReinforcedObsidianBlock(AbstractBlock.Settings.create()
                        .registryKey(blockKey("reinforced_obsidian"))
                        .mapColor(MapColor.BLACK).strength(50f, 1200f).requiresTool()
                        .sounds(BlockSoundGroup.STONE)));
        REINFORCED_OBSIDIAN_ITEM = registerBlockItem("reinforced_obsidian", REINFORCED_OBSIDIAN_BLOCK);

        GLOWSTONE_BRICKS_BLOCK = registerBlock("glowstone_bricks",
                new GlowStoneBricksBlock(AbstractBlock.Settings.create()
                        .registryKey(blockKey("glowstone_bricks"))
                        .mapColor(MapColor.YELLOW).strength(0.8f, 0.8f)
                        .sounds(BlockSoundGroup.STONE).luminance(s -> 15)));
        GLOWSTONE_BRICKS_ITEM = registerBlockItem("glowstone_bricks", GLOWSTONE_BRICKS_BLOCK);

        ARCANE_BRICKS_BLOCK = registerBlock("arcane_bricks",
                new ArcaneBricksBlock(AbstractBlock.Settings.create()
                        .registryKey(blockKey("arcane_bricks"))
                        .mapColor(MapColor.PURPLE).strength(3f, 6f).requiresTool()
                        .sounds(BlockSoundGroup.STONE).luminance(s -> 2)));
        ARCANE_BRICKS_ITEM = registerBlockItem("arcane_bricks", ARCANE_BRICKS_BLOCK);

        VOID_STONE_BLOCK = registerBlock("void_stone",
                new VoidStoneBlock(AbstractBlock.Settings.create()
                        .registryKey(blockKey("void_stone"))
                        .mapColor(MapColor.BLACK).strength(45f, 2400f).requiresTool()
                        .sounds(BlockSoundGroup.DEEPSLATE).luminance(s -> 1)));
        VOID_STONE_ITEM = registerBlockItem("void_stone", VOID_STONE_BLOCK);

        ETHER_CRYSTAL_BLOCK = registerBlock("ether_crystal",
                new EtherCrystalBlock(AbstractBlock.Settings.create()
                        .registryKey(blockKey("ether_crystal"))
                        .mapColor(MapColor.CYAN).strength(1f, 1f)
                        .sounds(BlockSoundGroup.AMETHYST_BLOCK).luminance(s -> 12)
                        .nonOpaque().allowsSpawning((st, w, p, e) -> false)));
        ETHER_CRYSTAL_ITEM = registerBlockItem("ether_crystal", ETHER_CRYSTAL_BLOCK);

        // ── OddityItem (legacy NBT-driven item kept for existing worlds) ─────
        Identifier oddityId = Identifier.of(MOD_ID, "oddity");
        ODDITY_ITEM_INSTANCE = new OddityItem(
                new Item.Settings()
                        .maxCount(1)
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, oddityId)));
        Registry.register(Registries.ITEM, oddityId, ODDITY_ITEM_INSTANCE);
        DynamicItemRegistry.registerOddity(ODDITY_ITEM_INSTANCE);
        LOG.info("[Alchemod] Registered legacy OddityItem for existing NBT-driven stacks.");

        // ── Legacy 64-slot pool (for items already in the world) ──────────────
        DynamicItemRegistry.register();

        ItemAbilityEvents.register();

        // ── Block entities ─────────────────────────────────────────────────────
        FORGE_BE_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "alchemical_forge"),
                FabricBlockEntityTypeBuilder.create(ForgeBlockEntity::new, FORGE_BLOCK).build());

        CREATOR_BE_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "item_creator"),
                FabricBlockEntityTypeBuilder.create(CreatorBlockEntity::new, CREATOR_BLOCK).build());

        BUILDER_BE_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "build_creator"),
                FabricBlockEntityTypeBuilder.create(BuilderBlockEntity::new, BUILDER_BLOCK).build());

        // ── Screen handlers ────────────────────────────────────────────────────
        FORGE_HANDLER = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "alchemical_forge"),
                new ScreenHandlerType<>(ForgeScreenHandler::new, FeatureSet.empty()));

        CREATOR_HANDLER = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "item_creator"),
                new ScreenHandlerType<>(CreatorScreenHandler::new, FeatureSet.empty()));

        BUILDER_HANDLER = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "build_creator"),
                new ScreenHandlerType<>(BuilderScreenHandler::new, FeatureSet.empty()));

        // ── Network ────────────────────────────────────────────────────────────
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(
                BuilderPromptPayload.ID, BuilderPromptPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(
                ForgeNbtPayload.ID, ForgeNbtPayload.CODEC);

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                BuilderPromptPayload.ID, (payload, context) -> context.server().execute(() -> {
                    var player   = context.player();
                    var world    = player.getServerWorld();
                    var targetPos = payload.pos();
                    if (player.currentScreenHandler instanceof BuilderScreenHandler bh
                            && bh.getBlockPos() != null) {
                        targetPos = bh.getBlockPos();
                    }
                    if (world.getBlockEntity(targetPos) instanceof BuilderBlockEntity builder) {
                        double dist = player.squaredDistanceTo(
                                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                        if (dist <= 64.0) {
                            builder.receivePrompt(payload.prompt(), world);
                        } else {
                            LOG.warn("[Alchemod] {} triggered builder from too far away",
                                    player.getName().getString());
                        }
                    }
                }));

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                ForgeNbtPayload.ID, (payload, context) -> context.server().execute(() -> {
                    var player = context.player();
                    // Derive the forge from the open screen handler, not from the payload pos
                    // (ForgeScreen was incorrectly sending BlockPos.ORIGIN).
                    if (player.currentScreenHandler instanceof ForgeScreenHandler forgeHandler
                            && forgeHandler.getInventory() instanceof ForgeBlockEntity forge) {
                        double dist = player.squaredDistanceTo(
                                forge.getPos().getX() + 0.5,
                                forge.getPos().getY() + 0.5,
                                forge.getPos().getZ() + 0.5);
                        if (dist <= 64.0) {
                            forge.applyCustomData(
                                    payload.customName(), payload.customLore(),
                                    payload.customColor(), payload.customEnchantments(),
                                    payload.hideFlags());
                        }
                    }
                }));

        // ── Creative tabs ──────────────────────────────────────────────────────
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(FORGE_ITEM);
            entries.add(CREATOR_ITEM);
            entries.add(BUILDER_ITEM);
            entries.add(ALCHEMICAL_GLASS_ITEM);
            entries.add(REINFORCED_OBSIDIAN_ITEM);
            entries.add(GLOWSTONE_BRICKS_ITEM);
            entries.add(ARCANE_BRICKS_ITEM);
            entries.add(VOID_STONE_ITEM);
            entries.add(ETHER_CRYSTAL_ITEM);
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
            entries.add(ALCHEMICAL_GLASS_ITEM);
            entries.add(REINFORCED_OBSIDIAN_ITEM);
            entries.add(GLOWSTONE_BRICKS_ITEM);
            entries.add(ARCANE_BRICKS_ITEM);
            entries.add(VOID_STONE_ITEM);
            entries.add(ETHER_CRYSTAL_ITEM);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static <T extends Block> T registerBlock(String name, T block) {
        return Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, name), block);
    }

    private static Item registerBlockItem(String name, Block block) {
        return Registry.register(Registries.ITEM, Identifier.of(MOD_ID, name),
                new BlockItem(block, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, name)))));
    }

    private static RegistryKey<Block> blockKey(String name) {
        return RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, name));
    }
}
