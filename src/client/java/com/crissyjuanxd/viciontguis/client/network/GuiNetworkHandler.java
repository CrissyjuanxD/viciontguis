package com.crissyjuanxd.viciontguis.client.network;

import com.crissyjuanxd.viciontguis.client.gui.DynamicGuiScreen;
import com.crissyjuanxd.viciontguis.client.gui.GuiElementFactory;
import com.crissyjuanxd.viciontguis.client.gui.OverlayManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;

public final class GuiNetworkHandler {

    private static String currentOpenGuiId = null;

    private GuiNetworkHandler() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(GuiPayload.ID, GuiPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GuiActionPayload.ID, GuiActionPayload.CODEC); // nuevo canal C2S

        ClientPlayNetworking.registerGlobalReceiver(GuiPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> handle(payload, client));
        });
    }

    private static void handle(GuiPayload payload, MinecraftClient client) {
        switch (payload.action()) {
            case OPEN, UPDATE -> {
                JsonObject guiData = JsonParser.parseString(payload.json()).getAsJsonObject();
                String target = guiData.has("target") ? guiData.get("target").getAsString() : "screen";

                if ("hud".equals(target) || "inventory".equals(target)) {
                    GuiElementFactory.ParseResult result = GuiElementFactory.parse(guiData, client.textRenderer);
                    OverlayManager.addOverlay(payload.guiId(), target, result);
                } else {
                    if (payload.action() == GuiPayload.Action.OPEN || (payload.guiId().equals(currentOpenGuiId) && client.currentScreen instanceof DynamicGuiScreen)) {
                        currentOpenGuiId = payload.guiId();
                        client.setScreen(new DynamicGuiScreen(payload.json(), action -> sendAction(payload.guiId(), action)));
                    }
                }
            }
            case CLOSE, DELETE -> {
                if (!OverlayManager.removeOverlay(payload.guiId())) {
                    if (payload.guiId().equals(currentOpenGuiId) && client.currentScreen instanceof DynamicGuiScreen screen) {
                        screen.close();
                        currentOpenGuiId = null;
                    }
                }
            }
        }
    }

    /** Usado por acciones sin gui explícita conocida (ej: botón G local). */
    public static void sendAction(String action) {
        sendAction(currentOpenGuiId, action);
    }

    /** Usado por overlays de HUD/inventario o botones del menú, envían directo al plugin. */
    public static void sendAction(String guiId, String action) {
        System.out.println("[ViciontGuis] Acción enviada al servidor: " + action + " (gui: " + guiId + ")");

        // Enviar la acción por la red hacia el Plugin de Bukkit/Paper
        if (ClientPlayNetworking.canSend(GuiActionPayload.ID)) {
            String safeGuiId = guiId != null ? guiId : "none";
            ClientPlayNetworking.send(new GuiActionPayload(safeGuiId, action));
        }
    }

    public static void simulatePayload(String id, String actionStr, String json) {
        handle(new GuiPayload(GuiPayload.Action.valueOf(actionStr), id, json), MinecraftClient.getInstance());
    }
}