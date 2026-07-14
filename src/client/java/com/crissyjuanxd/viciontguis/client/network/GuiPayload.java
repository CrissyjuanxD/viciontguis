package com.crissyjuanxd.viciontguis.client.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record GuiPayload(Action action, String guiId, String json) implements CustomPayload {

    public static final CustomPayload.Id<GuiPayload> ID = new CustomPayload.Id<>(Identifier.of("viciontguis", "gui"));

    public static final PacketCodec<RegistryByteBuf, GuiPayload> CODEC = PacketCodec.tuple(
            Action.CODEC, GuiPayload::action,
            PacketCodecs.string(32767), GuiPayload::guiId,
            PacketCodecs.string(2097152), GuiPayload::json,
            GuiPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public enum Action {
        OPEN,
        UPDATE,
        CLOSE,
        DELETE;

        public static final PacketCodec<RegistryByteBuf, Action> CODEC =
                PacketCodec.of(
                        (action, buf) -> buf.writeByte(action.ordinal()),
                        buf -> Action.values()[buf.readByte()]
                );
    }
}