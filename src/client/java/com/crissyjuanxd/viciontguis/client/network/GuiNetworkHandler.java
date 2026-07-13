package com.crissyjuanxd.viciontguis.client.network;

import com.crissyjuanxd.viciontguis.client.gui.DynamicGuiScreen;
import com.crissyjuanxd.viciontguis.client.gui.GuiTexturePreloader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;

/**
 * Registra el payload/canal y conecta los mensajes que manda el plugin con
 * DynamicGuiScreen. Esta es la pieza que reemplaza al menú de prueba hardcodeado
 * en ViciontGuisClient una vez que el plugin del servidor exista.
 * <p>
 * gui_id de la screen actualmente abierta se guarda acá para poder distinguir
 * un UPDATE/CLOSE dirigido a "la GUI que el jugador tiene abierta ahora" de uno
 * viejo que ya no corresponde (ej. el jugador ya cerró y abrió otra).
 */
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
            case OPEN -> {
                currentOpenGuiId = payload.guiId();
                client.setScreen(new DynamicGuiScreen(payload.json(), GuiNetworkHandler::sendAction));
            }
            case UPDATE -> {
                // Solo refrescamos si es la GUI que el jugador tiene abierta ahora mismo.
                if (payload.guiId().equals(currentOpenGuiId) && client.currentScreen instanceof DynamicGuiScreen) {
                    client.setScreen(new DynamicGuiScreen(payload.json(), GuiNetworkHandler::sendAction));
                }
            }
            case CLOSE -> {
                if (payload.guiId().equals(currentOpenGuiId) && client.currentScreen instanceof DynamicGuiScreen screen) {
                    screen.close();
                    currentOpenGuiId = null;
                }
            }
        }
    }

    /**
     * Callback que le pasamos a cada DynamicGuiScreen como actionHandler.
     * Por ahora solo loguea; acá es donde en el futuro se manda de vuelta al
     * servidor (vía otro canal C2S) qué botón tocó el jugador, para que el
     * plugin decida qué hacer (misión completada, receta vista, etc).
     */
    private static void sendAction(String action) {
        System.out.println("[ViciontGuis] Acción del jugador: " + action);
        // TODO: mandar al servidor por un canal C2S propio cuando exista el plugin.
    }

    /** Útil para que el mod pida precarga de texturas apenas llega el primer payload. */
    public static void warmTexturesFor(GuiPayload payload) {
        // Si más adelante el JSON trae una lista explícita de texturas, se puede
        // extraer acá y pasarla a GuiTexturePreloader.warmAll(...) antes de
        // siquiera parsear el resto del payload.
        GuiTexturePreloader.warmKnown();
    }
}