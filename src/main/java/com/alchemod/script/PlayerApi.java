package com.alchemod.script;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public final class PlayerApi {

    private final PlayerEntity player;
    private final ServerWorld world;

    public PlayerApi(PlayerEntity player, ServerWorld world) {
        this.player = player;
        this.world = world;
    }

    public void heal(double amount) {
        player.heal((float) Math.min(amount, 20.0));
    }

    public void damage(double amount) {
        player.damage(world, world.getDamageSources().magic(), (float) Math.min(amount, 20.0));
    }

    public double getHealth() {
        return player.getHealth();
    }

    public void addEffect(String name, int ticks, int level) {
        var effect = EffectResolver.resolve(name);
        if (effect == null) {
            return;
        }

        int safeTicks = Math.min(Math.max(ticks, 20), 12_000);
        int safeLevel = Math.min(Math.max(level, 0), 3);
        player.addStatusEffect(new StatusEffectInstance(effect, safeTicks, safeLevel));
    }

    public void removeEffect(String name) {
        var effect = EffectResolver.resolve(name);
        if (effect != null) {
            player.removeStatusEffect(effect);
        }
    }

    public void addVelocity(double x, double y, double z) {
        double cap = 3.0;
        player.addVelocity(
                Math.max(-cap, Math.min(cap, x)),
                Math.max(-cap, Math.min(cap, y)),
                Math.max(-cap, Math.min(cap, z)));
        player.velocityModified = true;
    }

    public void teleport(double x, double y, double z) {
        Vec3d currentPos = player.getPos();
        if (currentPos.distanceTo(new Vec3d(x, y, z)) > 64.0) {
            return;
        }

        player.teleport(x, y, z, true);
    }

    public void setOnFire(int seconds) {
        player.setOnFireFor(Math.min(seconds, 30));
    }

    public void extinguish() {
        player.extinguish();
    }

    public void sendMessage(String text) {
        player.sendMessage(Text.literal(text), false);
    }

    public double getX() {
        return player.getX();
    }

    public double getY() {
        return player.getY();
    }

    public double getZ() {
        return player.getZ();
    }

    public double[] getLookDir() {
        Vec3d look = player.getRotationVec(1.0f);
        return new double[]{look.x, look.y, look.z};
    }

    public boolean isSneaking() {
        return player.isSneaking();
    }

    public boolean isInWater() {
        return player.isTouchingWater();
    }
}
