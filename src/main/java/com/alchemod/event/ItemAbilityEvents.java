package com.alchemod.event;

import com.alchemod.AlchemodInit;
import com.alchemod.creator.DynamicItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Handles right-click abilities for {@link DynamicItem}s whose {@code creator_special}
 * is set but whose behavior is not covered by a script.
 *
 * <p>All data is read from the ItemStack's own NBT so that items created in previous
 * sessions continue to work correctly after a server restart.
 */
public class ItemAbilityEvents {

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!(stack.getItem() instanceof DynamicItem)) {
                return ActionResult.PASS;
            }

            // If the item has a script, DynamicItem.use() handles it — don't double-fire.
            String script = DynamicItem.readTag(stack, "creator_script");
            if (script != null && !script.isBlank()) {
                return ActionResult.PASS;
            }

            // Read special ability directly from NBT — works after restart.
            String special = DynamicItem.readTag(stack, "creator_special");
            if (special == null || special.isBlank()) {
                return ActionResult.PASS;
            }

            if (world.isClient) {
                return ActionResult.SUCCESS;
            }

            ServerWorld serverWorld = (ServerWorld) world;
            boolean didAction = applySpecial(special.trim(), player, serverWorld);

            if (didAction) {
                AlchemodInit.LOG.info("[Ability] {} used ability: {}",
                        player.getName().getString(), special);
            }

            return didAction ? ActionResult.SUCCESS : ActionResult.PASS;
        });
    }

    // ── Ability dispatch ──────────────────────────────────────────────────────

    private static boolean applySpecial(String special, PlayerEntity player, ServerWorld world) {
        return switch (special) {
            case "launch" -> {
                player.addVelocity(0, 1.5, 0);
                player.velocityModified = true;
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 0.8f, 1.0f);
                yield true;
            }
            case "phase" -> {
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.INVISIBILITY, 300, 0));
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.SPEED, 300, 2));
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.5f, 0.8f);
                yield true;
            }
            case "void_step" -> {
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.SLOW_FALLING, 400, 0));
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.LEVITATION, 100, 4));
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.0f);
                yield true;
            }
            case "freeze" -> {
                for (Entity e : world.getOtherEntities(player,
                        player.getBoundingBox().expand(5))) {
                    if (e instanceof LivingEntity le) {
                        le.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.SLOWNESS, 200, 4));
                        le.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.GLOWING, 100, 0));
                    }
                }
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_BUCKET_EMPTY_POWDER_SNOW, SoundCategory.PLAYERS, 0.8f, 1.2f);
                yield true;
            }
            case "knockback" -> {
                for (Entity e : world.getOtherEntities(player,
                        player.getBoundingBox().expand(6))) {
                    if (e instanceof LivingEntity le) {
                        Vec3d dir = e.getPos().subtract(player.getPos()).normalize();
                        le.setVelocity(dir.multiply(3.0).add(0, 0.5, 0));
                        le.velocityModified = true;
                    }
                }
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.8f, 1.0f);
                yield true;
            }
            case "drain" -> {
                for (Entity e : world.getOtherEntities(player,
                        player.getBoundingBox().expand(8))) {
                    if (e instanceof LivingEntity le) {
                        le.damage(world, player.getDamageSources().magic(), 4.0f);
                    }
                }
                player.heal(4.0f);
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.REGENERATION, 200, 2));
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.8f, 1.2f);
                yield true;
            }
            case "heal_aura" -> {
                player.heal(8.0f);
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.REGENERATION, 400, 3));
                for (Entity e : world.getOtherEntities(player,
                        player.getBoundingBox().expand(10))) {
                    if (e instanceof PlayerEntity pe) {
                        pe.heal(4.0f);
                        pe.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.REGENERATION, 200, 1));
                    }
                }
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                yield true;
            }
            case "barrier" -> {
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.RESISTANCE, 300, 3));
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE, 600, 0));
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_IRON_GOLEM_REPAIR, SoundCategory.PLAYERS, 0.7f, 1.0f);
                yield true;
            }
            case "surge" -> {
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.SPEED, 400, 3));
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.JUMP_BOOST, 400, 3));
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.HASTE, 400, 2));
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8f, 1.2f);
                yield true;
            }
            case "lightning" -> {
                Vec3d lookDir = player.getRotationVec(1.0f).multiply(15);
                Vec3d target  = player.getPos().add(lookDir);
                world.createExplosion(null, target.x, target.y, target.z, 4.0f,
                        World.ExplosionSourceType.TNT);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.0f, 1.0f);
                yield true;
            }
            case "ignite" -> {
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.8f, 1.0f);
                yield true;
            }
            default -> false;
        };
    }
}
