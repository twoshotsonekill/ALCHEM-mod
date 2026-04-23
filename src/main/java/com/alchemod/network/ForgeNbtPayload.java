package com.alchemod.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public record ForgeNbtPayload(
        BlockPos pos,
        String customName,
        String customLore,
        int customColor,
        List<String> customEnchantments,
        int hideFlags
) implements CustomPayload {

    public static final CustomPayload.Id<ForgeNbtPayload> ID =
            new CustomPayload.Id<>(Identifier.of("alchemod", "forge_nbt"));

    public static final PacketCodec<RegistryByteBuf, ForgeNbtPayload> CODEC =
            new PacketCodec<>() {
                @Override
                public ForgeNbtPayload decode(RegistryByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();
                    String name = buf.readString(64);
                    String lore = buf.readString(160);
                    int color = buf.readVarInt();
                    int enchantmentCount = buf.readVarInt();
                    List<String> enchantments = new ArrayList<>(enchantmentCount);
                    for (int index = 0; index < enchantmentCount; index++) {
                        enchantments.add(buf.readString(80));
                    }
                    int hideFlags = buf.readVarInt();
                    return new ForgeNbtPayload(pos, name, lore, color, enchantments, hideFlags);
                }

                @Override
                public void encode(RegistryByteBuf buf, ForgeNbtPayload value) {
                    buf.writeBlockPos(value.pos());
                    buf.writeString(value.customName(), 64);
                    buf.writeString(value.customLore(), 160);
                    buf.writeVarInt(value.customColor());
                    buf.writeVarInt(value.customEnchantments().size());
                    for (String enchantment : value.customEnchantments()) {
                        buf.writeString(enchantment, 80);
                    }
                    buf.writeVarInt(value.hideFlags());
                }
            };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
