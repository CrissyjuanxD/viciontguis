package com.crissyjuanxd.viciontguis.client.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// Cliente -> Servidor: "el jugador clickeó este botón en esta gui"
public record GuiActionPayload(String guiId, String action) implements CustomPayload {

    public static final CustomPayload.Id<GuiActionPayload> ID = new CustomPayload.Id<>(Identifier.of("viciontguis", "action"));

    public static final PacketCodec<RegistryByteBuf, GuiActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(32767), GuiActionPayload::guiId,
            PacketCodecs.string(32767), GuiActionPayload::action,
            GuiActionPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}