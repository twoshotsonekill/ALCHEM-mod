package com.alchemod.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record BuilderModePayload(BlockPos pos, int mode) implements CustomPayload {

    public static final CustomPayload.Id<BuilderModePayload> ID =
            new CustomPayload.Id<>(Identifier.of("alchemod", "builder_mode"));

    public static final PacketCodec<RegistryByteBuf, BuilderModePayload> CODEC =
            new PacketCodec<>() {
                @Override
                public BuilderModePayload decode(RegistryByteBuf buf) {
                    return new BuilderModePayload(buf.readBlockPos(), buf.readVarInt());
                }

                @Override
                public void encode(RegistryByteBuf buf, BuilderModePayload value) {
                    buf.writeBlockPos(value.pos());
                    buf.writeVarInt(value.mode());
                }
            };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
