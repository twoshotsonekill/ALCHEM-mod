package com.alchemod.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record BuilderPromptPayload(BlockPos pos, String prompt) implements CustomPayload {

    public static final CustomPayload.Id<BuilderPromptPayload> ID =
            new CustomPayload.Id<>(Identifier.of("alchemod", "builder_prompt"));

    public static final PacketCodec<RegistryByteBuf, BuilderPromptPayload> CODEC =
            new PacketCodec<>() {
                @Override
                public BuilderPromptPayload decode(RegistryByteBuf buf) {
                    return new BuilderPromptPayload(buf.readBlockPos(), buf.readString(512));
                }
                @Override
                public void encode(RegistryByteBuf buf, BuilderPromptPayload value) {
                    buf.writeBlockPos(value.pos());
                    buf.writeString(value.prompt(), 512);
                }
            };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}