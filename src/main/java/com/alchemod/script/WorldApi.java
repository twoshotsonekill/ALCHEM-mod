package com.alchemod.script;

import com.alchemod.AlchemodInit;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

public final class WorldApi {

    private final ServerWorld world;
    private final PlayerEntity player;

    public WorldApi(ServerWorld world, PlayerEntity player) {
        this.world = world;
        this.player = player;
    }

    public void createExplosion(double x, double y, double z, double power, boolean fire) {
        float safePower = (float) Math.min(Math.abs(power), 8.0);
        world.createExplosion(null, x, y, z, safePower, fire, World.ExplosionSourceType.TNT);
    }

    public void spawnLightning(double x, double y, double z) {
        var bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
        if (bolt == null) {
            return;
        }

        bolt.refreshPositionAfterTeleport(x, y, z);
        world.spawnEntity(bolt);
    }

    public void spawnMob(String entityId, double x, double y, double z) {
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        if (!pos.isWithinDistance(player.getBlockPos(), 24.0)) {
            return;
        }

        try {
            Identifier id = entityId.contains(":") ? Identifier.of(entityId) : Identifier.of("minecraft", entityId);
            EntityType<?> type = Registries.ENTITY_TYPE.get(id);
            if (type == null) {
                return;
            }

            Entity entity = type.create(world, SpawnReason.TRIGGERED);
            if (!(entity instanceof LivingEntity)) {
                return;
            }

            entity.refreshPositionAfterTeleport(x, y, z);
            world.spawnEntity(entity);
        } catch (Exception e) {
            AlchemodInit.LOG.debug("[ItemScript] Unknown mob: {}", entityId);
        }
    }

    public void playSound(String soundId, double volume, double pitch) {
        try {
            Identifier id = soundId.contains(":") ? Identifier.of(soundId) : Identifier.of("minecraft", soundId);
            SoundEvent sound = Registries.SOUND_EVENT.get(id);
            if (sound == null) {
                return;
            }

            world.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    sound,
                    SoundCategory.PLAYERS,
                    (float) Math.min(Math.abs(volume), 2.0),
                    (float) Math.max(0.5, Math.min(pitch, 2.0)));
        } catch (Exception e) {
            AlchemodInit.LOG.debug("[ItemScript] Unknown sound: {}", soundId);
        }
    }

    public void setBlock(double x, double y, double z, String blockId) {
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        if (!pos.isWithinDistance(player.getBlockPos(), 24.0)) {
            return;
        }

        try {
            Identifier id = blockId.contains(":") ? Identifier.of(blockId) : Identifier.of("minecraft", blockId);
            Block block = Registries.BLOCK.get(id);
            if (block != null) {
                world.setBlockState(pos, block.getDefaultState());
            }
        } catch (Exception e) {
            AlchemodInit.LOG.debug("[ItemScript] Unknown block: {}", blockId);
        }
    }

    public Object[] nearbyEntities(double radius) {
        double safeRadius = Math.min(Math.abs(radius), 32.0);
        Box searchBox = player.getBoundingBox().expand(safeRadius);
        List<Entity> entities = world.getOtherEntities(player, searchBox, entity -> entity instanceof LivingEntity living && living.isAlive());
        return entities.stream()
                .map(entity -> new EntityApi((LivingEntity) entity, world, player))
                .toArray();
    }

    public long getTime() {
        return world.getTime();
    }

    public boolean isDay() {
        return world.isDay();
    }

    public boolean isRaining() {
        return world.isRaining();
    }
}
