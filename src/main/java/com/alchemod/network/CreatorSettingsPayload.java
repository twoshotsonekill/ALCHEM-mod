package com.alchemod.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record CreatorSettingsPayload(BlockPos pos, boolean behaviorCodeEnabled) implements CustomPayload {

    public static final CustomPayload.Id<CreatorSettingsPayload> ID =
            new CustomPayload.Id<>(Identifier.of("alchemod", "creator_settings"));

    public static final PacketCodec<RegistryByteBuf, CreatorSettingsPayload> CODEC =
            new PacketCodec<>() {
                @Override
                public CreatorSettingsPayload decode(RegistryByteBuf buf) {
                    return new CreatorSettingsPayload(buf.readBlockPos(), buf.readBoolean());
                }

                @Override
                public void encode(RegistryByteBuf buf, CreatorSettingsPayload value) {
                    buf.writeBlockPos(value.pos());
                    buf.writeBoolean(value.behaviorCodeEnabled());
                }
            };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
