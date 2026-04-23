package com.alchemod;

import com.alchemod.ai.AlchemodConfig;
import com.alchemod.block.BuilderBlock;
import com.alchemod.block.BuilderBlockEntity;
import com.alchemod.block.CreatorBlock;
import com.alchemod.block.CreatorBlockEntity;
import com.alchemod.block.ForgeBlock;
import com.alchemod.block.ForgeBlockEntity;
import com.alchemod.creator.DynamicItemRegistry;
import com.alchemod.event.ItemAbilityEvents;
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
    public static Item FORGE_ITEM;
    public static Block CREATOR_BLOCK;
    public static Item CREATOR_ITEM;
    public static Block BUILDER_BLOCK;
    public static Item BUILDER_ITEM;

    public static BlockEntityType<ForgeBlockEntity> FORGE_BE_TYPE;
    public static BlockEntityType<CreatorBlockEntity> CREATOR_BE_TYPE;
    public static BlockEntityType<BuilderBlockEntity> BUILDER_BE_TYPE;
    public static ScreenHandlerType<ForgeScreenHandler> FORGE_HANDLER;
    public static ScreenHandlerType<CreatorScreenHandler> CREATOR_HANDLER;
    public static ScreenHandlerType<BuilderScreenHandler> BUILDER_HANDLER;

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

        DynamicItemRegistry.register();
        ItemAbilityEvents.register();

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

        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(
                BuilderPromptPayload.ID,
                BuilderPromptPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(
                ForgeNbtPayload.ID,
                ForgeNbtPayload.CODEC);

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
                                    targetPos.getX() + 0.5,
                                    targetPos.getY() + 0.5,
                                    targetPos.getZ() + 0.5);
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
                                        payload.customName(),
                                        payload.customLore(),
                                        payload.customColor(),
                                        payload.customEnchantments(),
                                        payload.hideFlags());
                            }
                        }
                    });
                });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(FORGE_ITEM);
            entries.add(CREATOR_ITEM);
            entries.add(BUILDER_ITEM);
        });
    }
}
