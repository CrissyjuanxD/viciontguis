package com.crissyjuanxd.viciontguis.client.network;

import com.crissyjuanxd.viciontguis.client.ViciontGuisClient;
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
                        client.setScreen(new DynamicGuiScreen(payload.json(), GuiNetworkHandler::sendAction));
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

    public static void sendAction(String action) {
        System.out.println("[ViciontGuis] Acción del jugador: " + action);
        // Fix: antes esto no hacía nada más que loggear, por eso el botón
        // del inventario no abría nada aunque la G sí funcionara.
        ViciontGuisClient.handleAction(action);
        // TODO: cuando exista el canal real hacia el plugin, aquí también se envía el packet al server.
    }

    public static void simulatePayload(String id, String actionStr, String json) {
        handle(new GuiPayload(GuiPayload.Action.valueOf(actionStr), id, json), MinecraftClient.getInstance());
    }
}