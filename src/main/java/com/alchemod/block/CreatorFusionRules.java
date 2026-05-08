package com.alchemod.block;

import java.util.List;
import java.util.Locale;

final class CreatorFusionRules {

    private CreatorFusionRules() {
    }

    static Fusion match(String itemA, String itemB) {
        String ingredients = normalise(itemA + " " + itemB);
        boolean hasTnt = hasAny(ingredients, "tnt", "gunpowder", "fire charge", "creeper");
        boolean hasBow = hasAny(ingredients, "bow", "crossbow", "arrow");
        boolean hasDirt = hasAny(ingredients, "dirt", "coarse dirt", "rooted dirt", "grass block", "mud");
        boolean hasIron = hasAny(ingredients, "iron", "iron ingot", "iron block", "raw iron");

        if (hasTnt && hasBow) {
            return explosiveBow();
        }
        if (hasDirt && hasIron) {
            return landmineBlock();
        }
        if (hasTnt && (hasDirt || hasIron)) {
            return landmineBlock();
        }
        return null;
    }

    private static Fusion explosiveBow() {
        return new Fusion(
                "Explosive Bow",
                "A reinforced bow that turns each shot into a short-range TNT burst.",
                "16x16 pixel art bow strapped with red TNT sticks and lit fuse",
                "rare",
                "bow",
                List.of("strength", "fire_resistance"),
                "ignite",
                null,
                """
                function onUse(player, world) {
                  var look = player.getLookDir();
                  var x = player.getX()+look[0]*9;
                  var y = player.getY()+1.2+look[1]*5;
                  var z = player.getZ()+look[2]*9;
                  world.createExplosion(x, y, z, 3.2, true);
                  var t = nearbyEntities(7); for(var i=0;i<t.length;i++){t[i].knockbackFrom(2.8); t[i].setOnFire(4);}
                  world.playSound('entity.firework_rocket.launch', 1.0, 0.7);
                  player.sendMessage('Explosive Bow launches a TNT charge.');
                }
                """);
    }

    private static Fusion landmineBlock() {
        return new Fusion(
                "Landmine Block",
                "A dirt-caked iron charge that plants a pressure-plate trap or detonates when sneaking.",
                "16x16 pixel art dirt block with iron pressure plate and red fuse",
                "rare",
                "block",
                List.of("resistance"),
                "ignite",
                null,
                """
                function onUse(player, world) {
                  var look = player.getLookDir();
                  var x = player.getX()+look[0]*2;
                  var y = player.getY()-1;
                  var z = player.getZ()+look[2]*2;
                  world.setBlock(x, y, z, 'minecraft:tnt');
                  world.setBlock(x, y+1, z, 'minecraft:stone_pressure_plate');
                  var t = nearbyEntities(4); for(var i=0;i<t.length;i++){t[i].addEffect('slowness',80,1);}
                  if(player.isSneaking()){world.createExplosion(x, y+1, z, 3.0, false); player.sendMessage('Landmine Block detonates on your mark.');}
                  else{player.sendMessage('Landmine Block arms a pressure plate charge.');}
                  world.playSound('block.gravel.place', 0.8, 0.8);
                }
                """);
    }

    private static boolean hasAny(String ingredients, String... needles) {
        for (String needle : needles) {
            String token = normalise(needle).trim();
            if (ingredients.contains(" " + token + " ")) {
                return true;
            }
        }
        return false;
    }

    private static String normalise(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return " " + lower
                .replace(':', ' ')
                .replace('_', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim() + " ";
    }

    record Fusion(
            String name,
            String description,
            String spritePrompt,
            String rarity,
            String itemType,
            List<String> effects,
            String special,
            String mobType,
            String behaviorScript
    ) {
    }
}
