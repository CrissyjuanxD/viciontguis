package com.crissyjuanxd.viciontguis.client.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Payload que el plugin (Paper/Spigot, del lado servidor) manda al mod cliente
 * mediante un canal de plugin messaging para controlar las GUIs dinámicas.
 * <p>
 * El plugin NO corre Fabric, así que del lado servidor no se registra este
 * payload con la API de networking de Fabric: el plugin arma los bytes a mano
 * (mismo orden: action, guiId, json) sobre el canal "viciontguis:gui" y los manda
 * con {@code player.sendPluginMessage(...)}. Del lado del mod, Fabric decodifica
 * esos bytes con el mismo CODEC de acá.
 * <p>
 * Esto es análogo al canal que usa Viciont Media para mandar la orden de reproducir
 * un video/imagen a los clientes: un solo canal, un "action" que dice qué hacer,
 * y un payload de datos específico de esa acción.
 *
 * @param action tipo de operación: OPEN, UPDATE o CLOSE
 * @param guiId  identificador lógico de la GUI (para poder actualizar/cerrar la
 *               correcta si el jugador tiene una abierta)
 * @param json   el JSON completo de la GUI (vacío para CLOSE)
 */
public record GuiPayload(Action action, String guiId, String json) implements CustomPayload {

    public static final CustomPayload.Id<GuiPayload> ID = new CustomPayload.Id<>(Identifier.of("viciontguis", "gui"));

    public static final PacketCodec<RegistryByteBuf, GuiPayload> CODEC = PacketCodec.tuple(
            Action.CODEC, GuiPayload::action,
            PacketCodecs.string(32767), GuiPayload::guiId,
            PacketCodecs.string(2097152), GuiPayload::json, // hasta ~2MB de JSON, ajustar si hace falta
            GuiPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public enum Action {
        OPEN,
        UPDATE,
        CLOSE;

        public static final PacketCodec<RegistryByteBuf, Action> CODEC =
                PacketCodec.of(
                        (action, buf) -> buf.writeByte(action.ordinal()),
                        buf -> Action.values()[buf.readByte()]
                );
    }
}