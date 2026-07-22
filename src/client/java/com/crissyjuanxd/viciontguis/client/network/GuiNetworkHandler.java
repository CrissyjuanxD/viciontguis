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
        PayloadTypeRegistry.playC2S().register(GuiActionPayload.ID, GuiActionPayload.CODEC);

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
                    if (payload.action() == GuiPayload.Action.UPDATE
                            && payload.guiId().equals(currentOpenGuiId)
                            && client.currentScreen instanceof DynamicGuiScreen currentScreen) {
                        currentScreen.updateData(payload.json());
                    } else {
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

    public static void sendAction(String action) {
        sendAction(currentOpenGuiId, action);
    }

    public static void sendAction(String guiId, String action) {
        System.out.println("[ViciontGuis] Acción enviada al servidor: " + action + " (gui: " + guiId + ")");
        if (ClientPlayNetworking.canSend(GuiActionPayload.ID)) {
            String safeGuiId = guiId != null ? guiId : "none";
            ClientPlayNetworking.send(new GuiActionPayload(safeGuiId, action));
        }
    }

    public static void simulatePayload(String id, String actionStr, String json) {
        handle(new GuiPayload(GuiPayload.Action.valueOf(actionStr), id, json), MinecraftClient.getInstance());
    }
}