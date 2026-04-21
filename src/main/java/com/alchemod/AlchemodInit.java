package com.alchemod;

import com.alchemod.block.CreatorBlock;
import com.alchemod.block.CreatorBlockEntity;
import com.alchemod.block.ForgeBlock;
import com.alchemod.block.ForgeBlockEntity;
import com.alchemod.creator.DynamicItemRegistry;
import com.alchemod.screen.CreatorScreenHandler;
import com.alchemod.screen.ForgeScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
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

    public static final String OPENROUTER_KEY =
            System.getenv("OPENROUTER_API_KEY") != null ? System.getenv("OPENROUTER_API_KEY") : "";

    // Alchemical Forge — combines into existing vanilla items
    public static final Block FORGE_BLOCK = Registry.register(
            Registries.BLOCK, Identifier.of(MOD_ID, "alchemical_forge"),
            new ForgeBlock(AbstractBlock.Settings.create()
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "alchemical_forge")))
                    .mapColor(MapColor.PURPLE).strength(4f, 8f).requiresTool()
                    .sounds(BlockSoundGroup.METAL)));

    public static final Item FORGE_ITEM = Registry.register(
            Registries.ITEM, Identifier.of(MOD_ID, "alchemical_forge"),
            new BlockItem(FORGE_BLOCK, new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "alchemical_forge")))));

    // Item Creator — invents brand-new items with AI-generated sprites
    public static final Block CREATOR_BLOCK = Registry.register(
            Registries.BLOCK, Identifier.of(MOD_ID, "item_creator"),
            new CreatorBlock(AbstractBlock.Settings.create()
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "item_creator")))
                    .mapColor(MapColor.GOLD).strength(4f, 8f).requiresTool()
                    .sounds(BlockSoundGroup.METAL)));

    public static final Item CREATOR_ITEM = Registry.register(
            Registries.ITEM, Identifier.of(MOD_ID, "item_creator"),
            new BlockItem(CREATOR_BLOCK, new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "item_creator")))));

    public static BlockEntityType<ForgeBlockEntity>   FORGE_BE_TYPE;
    public static BlockEntityType<CreatorBlockEntity> CREATOR_BE_TYPE;
    public static ScreenHandlerType<ForgeScreenHandler>   FORGE_HANDLER;
    public static ScreenHandlerType<CreatorScreenHandler> CREATOR_HANDLER;

    @Override
    public void onInitialize() {
        LOG.info("Alchemod initialising — API key: {}", !OPENROUTER_KEY.isBlank() ? "SET" : "MISSING");

        DynamicItemRegistry.register(); // must be before registry freeze

        FORGE_BE_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "alchemical_forge"),
                FabricBlockEntityTypeBuilder.create(ForgeBlockEntity::new, FORGE_BLOCK).build());

        CREATOR_BE_TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(MOD_ID, "item_creator"),
                FabricBlockEntityTypeBuilder.create(CreatorBlockEntity::new, CREATOR_BLOCK).build());

        FORGE_HANDLER = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "alchemical_forge"),
                new ScreenHandlerType<>(ForgeScreenHandler::new, FeatureSet.empty()));

        CREATOR_HANDLER = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "item_creator"),
                new ScreenHandlerType<>(CreatorScreenHandler::new, FeatureSet.empty()));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e -> {
            e.add(FORGE_ITEM);
            e.add(CREATOR_ITEM);
        });
    }
}
