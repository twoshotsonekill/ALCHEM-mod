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
import com.alchemod.block.InfuserBlock;
import com.alchemod.block.InfuserBlockEntity;
import com.alchemod.block.ReinforcedObsidianBlock;
import com.alchemod.block.TransmuterBlock;
import com.alchemod.block.TransmuterBlockEntity;
import com.alchemod.block.VoidStoneBlock;
import com.alchemod.creator.DynamicItemRegistry;
import com.alchemod.event.ItemAbilityEvents;
import com.alchemod.item.AlchemicalArmorItem;
import com.alchemod.item.AlchemicalEssenceItem;
import com.alchemod.item.AlchemicalWandItem;
import com.alchemod.item.OddityItem;
import com.alchemod.network.BuilderPromptPayload;
import com.alchemod.network.ForgeNbtPayload;
import com.alchemod.screen.BuilderScreenHandler;
import com.alchemod.screen.CreatorScreenHandler;
import com.alchemod.screen.ForgeScreenHandler;
import com.alchemod.screen.InfuserScreenHandler;
import com.alchemod.screen.TransmuterScreenHandler;
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
    public static Item FORGE_ITEM;
    public static Block CREATOR_BLOCK;
    public static Item CREATOR_ITEM;
    public static Block BUILDER_BLOCK;
    public static Item BUILDER_ITEM;

    // Decorative blocks
    public static Block ALCHEMICAL_GLASS_BLOCK;
    public static Item ALCHEMICAL_GLASS_ITEM;
    public static Block REINFORCED_OBSIDIAN_BLOCK;
    public static Item REINFORCED_OBSIDIAN_ITEM;
    public static Block GLOWSTONE_BRICKS_BLOCK;
    public static Item GLOWSTONE_BRICKS_ITEM;
    public static Block ARCANE_BRICKS_BLOCK;
    public static Item ARCANE_BRICKS_ITEM;
    public static Block VOID_STONE_BLOCK;
    public static Item VOID_STONE_ITEM;
    public static Block ETHER_CRYSTAL_BLOCK;
    public static Item ETHER_CRYSTAL_ITEM;

    // Currency item
    public static Item ESSENCE_ITEM;

    public static BlockEntityType<ForgeBlockEntity> FORGE_BE_TYPE;
    public static BlockEntityType<CreatorBlockEntity> CREATOR_BE_TYPE;
    public static BlockEntityType<BuilderBlockEntity> BUILDER_BE_TYPE;
    public static BlockEntityType<InfuserBlockEntity> INFUSER_BE_TYPE;
    public static ScreenHandlerType<ForgeScreenHandler> FORGE_HANDLER;
    public static ScreenHandlerType<CreatorScreenHandler> CREATOR_HANDLER;
    public static ScreenHandlerType<BuilderScreenHandler> BUILDER_HANDLER;
    public static ScreenHandlerType<InfuserScreenHandler> INFUSER_HANDLER;
    public static BlockEntityType<TransmuterBlockEntity> TRANS_MUTER_BE_TYPE;
    public static ScreenHandlerType<TransmuterScreenHandler> TRANS_MUTER_HANDLER;

    @Override
    public void onInitialize() {
        CONFIG = AlchemodConfig.load(FabricLoader.getInstance().getConfigDir().resolve("alchemod.properties"), LOG);
        OPENROUTER_KEY = CONFIG.openRouterApiKey();
        LOG.info("Alchemod initialising - API key: {}", !OPENROUTER_KEY.isBlank() ? "SET" : "MISSING");

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

        // Alchemical Glass
        ALCHEMICAL_GLASS_BLOCK = Registry.register(
                Registries.BLOCK, Identifier.of(MOD_ID, "alchemical_glass"),
                new AlchemicalGlassBlock(AbstractBlock.Settings.create()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "alchemical_glass")))
                        .mapColor(MapColor.CYAN).strength(0.3f, 0.3f)
                        .sounds(BlockSoundGroup.GLASS).luminance(s -> 2)
                        .nonOpaque().allowsSpawning((state, world, pos, entity) -> false)));

        ALCHEMICAL_GLASS_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "alchemical_glass"),
                new BlockItem(ALCHEMICAL_GLASS_BLOCK, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "alchemical_glass")))));

        // Reinforced Obsidian
        REINFORCED_OBSIDIAN_BLOCK = Registry.register(
                Registries.BLOCK, Identifier.of(MOD_ID, "reinforced_obsidian"),
                new ReinforcedObsidianBlock(AbstractBlock.Settings.create()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "reinforced_obsidian")))
                        .mapColor(MapColor.BLACK).strength(50f, 1200f).requiresTool()
                        .sounds(BlockSoundGroup.STONE)));

        REINFORCED_OBSIDIAN_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "reinforced_obsidian"),
                new BlockItem(REINFORCED_OBSIDIAN_BLOCK, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "reinforced_obsidian")))));

        // Glowstone Bricks
        GLOWSTONE_BRICKS_BLOCK = Registry.register(
                Registries.BLOCK, Identifier.of(MOD_ID, "glowstone_bricks"),
                new GlowStoneBricksBlock(AbstractBlock.Settings.create()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "glowstone_bricks")))
                        .mapColor(MapColor.YELLOW).strength(0.8f, 0.8f)
                        .sounds(BlockSoundGroup.STONE).luminance(s -> 15)));

        GLOWSTONE_BRICKS_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "glowstone_bricks"),
                new BlockItem(GLOWSTONE_BRICKS_BLOCK, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "glowstone_bricks")))));

        // Arcane Bricks
        ARCANE_BRICKS_BLOCK = Registry.register(
                Registries.BLOCK, Identifier.of(MOD_ID, "arcane_bricks"),
                new ArcaneBricksBlock(AbstractBlock.Settings.create()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "arcane_bricks")))
                        .mapColor(MapColor.PURPLE).strength(3f, 6f).requiresTool()
                        .sounds(BlockSoundGroup.STONE).luminance(s -> 2)));

        ARCANE_BRICKS_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "arcane_bricks"),
                new BlockItem(ARCANE_BRICKS_BLOCK, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "arcane_bricks")))));

        // Void Stone
        VOID_STONE_BLOCK = Registry.register(
                Registries.BLOCK, Identifier.of(MOD_ID, "void_stone"),
                new VoidStoneBlock(AbstractBlock.Settings.create()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "void_stone")))
                        .mapColor(MapColor.BLACK).strength(45f, 2400f).requiresTool()
                        .sounds(BlockSoundGroup.DEEPSLATE).luminance(s -> 1)));

        VOID_STONE_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "void_stone"),
                new BlockItem(VOID_STONE_BLOCK, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "void_stone")))));

        // Ether Crystal
        ETHER_CRYSTAL_BLOCK = Registry.register(
                Registries.BLOCK, Identifier.of(MOD_ID, "ether_crystal"),
                new EtherCrystalBlock(AbstractBlock.Settings.create()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "ether_crystal")))
                        .mapColor(MapColor.CYAN).strength(1f, 1f)
                        .sounds(BlockSoundGroup.AMETHYST_BLOCK).luminance(s -> 12)
                        .nonOpaque().allowsSpawning((state, world, pos, entity) -> false)));

        ETHER_CRYSTAL_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "ether_crystal"),
                new BlockItem(ETHER_CRYSTAL_BLOCK, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "ether_crystal")))));

        // Alchemical Essence - primary currency item
        ESSENCE_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "alchemical_essence"),
                new AlchemicalEssenceItem(new Item.Settings()
                        .maxCount(64)
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "alchemical_essence")))));

        OddityItem oddityItem = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "oddity"),
                new OddityItem(new Item.Settings()
                        .maxCount(1)
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "oddity")))));
        DynamicItemRegistry.registerOddity(oddityItem);

        DynamicItemRegistry.register();
        ItemAbilityEvents.register();

        // Alchemical Wand
        Item WAND_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "alchemical_wand"),
                new AlchemicalWandItem(new Item.Settings()
                        .maxCount(1)
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "alchemical_wand")))));

        // Alchemical Armor Set
        Item HELMET_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "alchemical_helmet"),
                new AlchemicalArmorItem(net.minecraft.entity.EquipmentSlot.HEAD, new Item.Settings()
                        .maxCount(1)
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "alchemical_helmet")))));
        Item CHESTPLATE_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "alchemical_chestplate"),
                new AlchemicalArmorItem(net.minecraft.entity.EquipmentSlot.CHEST, new Item.Settings()
                        .maxCount(1)
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "alchemical_chestplate")))));
        Item LEGGINGS_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "alchemical_leggings"),
                new AlchemicalArmorItem(net.minecraft.entity.EquipmentSlot.LEGS, new Item.Settings()
                        .maxCount(1)
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "alchemical_leggings")))));
        Item BOOTS_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "alchemical_boots"),
                new AlchemicalArmorItem(net.minecraft.entity.EquipmentSlot.FEET, new Item.Settings()
                        .maxCount(1)
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "alchemical_boots")))));

        FORGE_BE_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "alchemical_forge"),
                FabricBlockEntityTypeBuilder.create(ForgeBlockEntity::new, FORGE_BLOCK).build());

        CREATOR_BE_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "item_creator"),
                FabricBlockEntityTypeBuilder.create(CreatorBlockEntity::new, CREATOR_BLOCK).build());

        BUILDER_BE_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "build_creator"),
                FabricBlockEntityTypeBuilder.create(BuilderBlockEntity::new, BUILDER_BLOCK).build());

        // Alchemical Infuser Block
        Block INFUSER_BLOCK = Registry.register(
                Registries.BLOCK, Identifier.of(MOD_ID, "alchemical_infuser"),
                new InfuserBlock(AbstractBlock.Settings.create()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "alchemical_infuser")))
                        .mapColor(MapColor.PURPLE).strength(4f, 8f).requiresTool()
                        .sounds(BlockSoundGroup.METAL).luminance(s -> 4)));

        Item INFUSER_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "alchemical_infuser"),
                new BlockItem(INFUSER_BLOCK, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "alchemical_infuser")))));

        INFUSER_BE_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "alchemical_infuser"),
                FabricBlockEntityTypeBuilder.create(InfuserBlockEntity::new, INFUSER_BLOCK).build());

        // Alchemical Transmuter Block
        Block TRANS_MUTER_BLOCK = Registry.register(
                Registries.BLOCK, Identifier.of(MOD_ID, "alchemical_transmuter"),
                new TransmuterBlock(AbstractBlock.Settings.create()
                        .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "alchemical_transmuter")))
                        .mapColor(MapColor.ORANGE).strength(4f, 8f).requiresTool()
                        .sounds(BlockSoundGroup.METAL).luminance(s -> 3)));

        Item TRANS_MUTER_ITEM = Registry.register(
                Registries.ITEM, Identifier.of(MOD_ID, "alchemical_transmuter"),
                new BlockItem(TRANS_MUTER_BLOCK, new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "alchemical_transmuter")))));

        TRANS_MUTER_BE_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "alchemical_transmuter"),
                FabricBlockEntityTypeBuilder.create(TransmuterBlockEntity::new, TRANS_MUTER_BLOCK).build());

        FORGE_HANDLER = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "alchemical_forge"),
                new ScreenHandlerType<>(ForgeScreenHandler::new, FeatureSet.empty()));

        CREATOR_HANDLER = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "item_creator"),
                new ScreenHandlerType<>(CreatorScreenHandler::new, FeatureSet.empty()));

        BUILDER_HANDLER = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "build_creator"),
                new ScreenHandlerType<>(BuilderScreenHandler::new, FeatureSet.empty()));

        INFUSER_HANDLER = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "alchemical_infuser"),
                new ScreenHandlerType<>(InfuserScreenHandler::new, FeatureSet.empty()));

        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(
                BuilderPromptPayload.ID, BuilderPromptPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(
                ForgeNbtPayload.ID, ForgeNbtPayload.CODEC);

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                BuilderPromptPayload.ID, (payload, context) -> {
                    context.server().execute(() -> {
                        var player = context.player();
                        var world = player.getServerWorld();
                        var targetPos = payload.pos();
                        if (player.currentScreenHandler instanceof BuilderScreenHandler builderHandler
                                && builderHandler.getBlockPos() != null) {
                            targetPos = builderHandler.getBlockPos();
                        }
                        var be = world.getBlockEntity(targetPos);
                        if (be instanceof BuilderBlockEntity builder) {
                            double dist = player.squaredDistanceTo(
                                    targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                            if (dist <= 64.0) {
                                builder.receivePrompt(payload.prompt(), world);
                            } else {
                                LOG.warn("[Alchemod] {} tried to trigger builder from too far", player.getName().getString());
                            }
                        }
                    });
                });

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                ForgeNbtPayload.ID, (payload, context) -> {
                    context.server().execute(() -> {
                        var player = context.player();
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
                    });
                });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(FORGE_ITEM);
            entries.add(CREATOR_ITEM);
            entries.add(BUILDER_ITEM);
            entries.add(INFUSER_ITEM);
            entries.add(ESSENCE_ITEM);
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
}
