package com.alchemod.script;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public final class EntityApi {

    private final LivingEntity entity;
    private final ServerWorld world;
    private final PlayerEntity player;

    public EntityApi(LivingEntity entity, ServerWorld world, PlayerEntity player) {
        this.entity = entity;
        this.world = world;
        this.player = player;
    }

    public void damage(double amount) {
        entity.damage(world, world.getDamageSources().magic(), (float) Math.min(amount, 40.0));
    }

    public void heal(double amount) {
        entity.heal((float) Math.min(amount, 20.0));
    }

    public double getHealth() {
        return entity.getHealth();
    }

    public boolean isAlive() {
        return entity.isAlive();
    }

    public void addEffect(String name, int ticks, int level) {
        var effect = EffectResolver.resolve(name);
        if (effect == null) {
            return;
        }

        int safeTicks = Math.min(Math.max(ticks, 20), 12_000);
        int safeLevel = Math.min(Math.max(level, 0), 3);
        entity.addStatusEffect(new StatusEffectInstance(effect, safeTicks, safeLevel));
    }

    public void setOnFire(int seconds) {
        entity.setOnFireFor(Math.min(seconds, 30));
    }

    public void extinguish() {
        entity.extinguish();
    }

    public void setVelocity(double x, double y, double z) {
        double cap = 4.0;
        entity.setVelocity(
                Math.max(-cap, Math.min(cap, x)),
                Math.max(-cap, Math.min(cap, y)),
                Math.max(-cap, Math.min(cap, z)));
        entity.velocityModified = true;
    }

    public void knockbackFrom(double strength) {
        Vec3d direction = entity.getPos().subtract(player.getPos());
        if (direction.lengthSquared() < 0.0001) {
            return;
        }

        double safeStrength = Math.min(Math.max(strength, 0.0), 8.0);
        entity.setVelocity(direction.normalize().multiply(safeStrength).add(0.0, 0.4, 0.0));
        entity.velocityModified = true;
    }

    public double getX() {
        return entity.getX();
    }

    public double getY() {
        return entity.getY();
    }

    public double getZ() {
        return entity.getZ();
    }

    public String getType() {
        return entity.getType().getUntranslatedName()
                .replace("entity.minecraft.", "")
                .replace("entity.", "");
    }

    public boolean isPlayer() {
        return entity instanceof PlayerEntity;
    }
}
