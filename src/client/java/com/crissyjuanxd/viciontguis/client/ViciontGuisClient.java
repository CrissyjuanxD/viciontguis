package com.crissyjuanxd.viciontguis.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ViciontGuisClient implements ClientModInitializer {

    private static KeyBinding testGuiKey;

    // ==========================================
    // ESTADO DE "CAMBIOS" (temporal, en memoria)
    // ==========================================
    // En un futuro esto lo va a alimentar el plugin vía un comando tipo
    // /cambio <numero> <leido:true|false> <json_message>
    // Por ahora es data de prueba en el cliente para probar la lógica y el look de la GUI.
    private static final int CAMBIOS_COLS = 4;
    private static final int CAMBIOS_ROWS = 4;
    private static final int CAMBIOS_COUNT = CAMBIOS_COLS * CAMBIOS_ROWS; // 16
    private static final Map<Integer, Boolean> cambiosLeidos = new HashMap<>();
    private static final Map<Integer, String> cambiosMensajes = new HashMap<>();

    static {
        // Cambio #1: el ejemplo tellraw que diste (corto).
        cambiosMensajes.put(1, """
            ["",{"text":"\u06de","bold":true,"color":"yellow"},{"text":" Cambio de dificultad","bold":true,"color":"#E69F33"},{"text":" \u25ba","bold":true,"color":"gray"},{"text":"\n\n"},{"text":"Ejemplo de Json message de Minecraft vanilla\nque se puede poner en un","color":"#6DBAEC"},{"text":" tellraw","bold":true,"color":"#E08C0E"}]
            """);

        // Cambio #2: mensaje de longitud media (para ver el achicado leve).
        cambiosMensajes.put(2, """
            ["",{"text":"\u2694","bold":true,"color":"red"},{"text":" Cambio de combate","bold":true,"color":"#E74C3C"},{"text":" \u25ba","bold":true,"color":"gray"},{"text":"\n\n"},{"text":"Se ha reducido el daño base de las espadas de netherite en un 10% para balancear el PvP del servidor. Ademas, el cooldown de los escudos ahora dura 0.5 segundos menos.","color":"#FFFFFF"},{"text":"\n\n"},{"text":"Esto aplica a todas las espadas, incluidas las corruptas.","color":"#AAAAAA","italic":true}]
            """);

        // Cambio #3: mensaje largo (para forzar el achicado fuerte y probar que no se corta).
        cambiosMensajes.put(3, """
            ["",{"text":"\u2699","bold":true,"color":"aqua"},{"text":" Rework del sistema de misiones","bold":true,"color":"#3498DB"},{"text":" \u25ba","bold":true,"color":"gray"},{"text":"\n\n"},{"text":"Hemos reescrito por completo el sistema de misiones desde cero para mejorar el rendimiento del servidor y arreglar bugs historicos. A continuacion un resumen extenso de todos los cambios incluidos en esta actualizacion, trabajada durante varias semanas.","color":"#FFFFFF"},{"text":"\n\n"},{"text":"1. Las misiones ahora se guardan por jugador en una base de datos separada.\n2. Se corrigio un bug donde el progreso se reiniciaba al reconectar.\n3. Las recompensas ahora pueden incluir items con custom model data.\n4. Se agrego soporte para misiones diarias y semanales.\n5. El menu de misiones ahora carga instantaneamente.","color":"#DDDDDD"},{"text":"\n\n"},{"text":"Si encuentran algun bug reportenlo en el Discord del servidor.","color":"#F1C40F","italic":true}]
            """);

        // Cambio #4: mensaje corto-medio de ejemplo.
        cambiosMensajes.put(4, """
            ["",{"text":"\u2302","bold":true,"color":"green"},{"text":" Cambios en SistemaTumbas","bold":true,"color":"#2ECC71"},{"text":" \u25ba","bold":true,"color":"gray"},{"text":"\n\n"},{"text":"Las tumbas ahora se generan correctamente incluso si mueres en el aire, y el temporizador ya no se corrompe visualmente al usar fuentes personalizadas.","color":"#FFFFFF"}]
            """);
    }

    @Override
    public void onInitializeClient() {
        testGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Abrir Menu Principal",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "Viciont Menu"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (testGuiKey.wasPressed()) {
                if (client.currentScreen instanceof DynamicGuiScreen) {
                    ((DynamicGuiScreen) client.currentScreen).close();
                } else if (client.currentScreen == null) {
                    openMainMenu(client);
                }
            }
        });
    }

    public static void handleAction(String action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if ("open_misiones".equals(action)) {
            client.setScreen(new DynamicGuiScreen(getMisionesJson(), ViciontGuisClient::handleAction));
        } else if ("open_recetas".equals(action)) {
            client.setScreen(new DynamicGuiScreen(getRecetasJson(), ViciontGuisClient::handleAction));
        } else if ("open_entidades".equals(action)) {
            client.setScreen(new DynamicGuiScreen(getEntidadesJson(), ViciontGuisClient::handleAction));
        } else if ("open_cambios".equals(action)) {
            client.setScreen(new DynamicGuiScreen(getCambiosJson(), ViciontGuisClient::handleAction));
        } else if ("open_main".equals(action)) {
            client.setScreen(new DynamicGuiScreen(getMainMenuJson(), ViciontGuisClient::handleAction));

            // --- EVENTOS DE APERTURA DE RECETAS ---
        } else if ("view_recipe_corrupted_steak".equals(action)) {
            client.setScreen(new DynamicGuiScreen(getRecipeOverlayCraftingTable(), ViciontGuisClient::handleAction));
        } else if ("view_recipe_corrupted_scrap".equals(action)) {
            client.setScreen(new DynamicGuiScreen(getRecipeOverlayFurnace(), ViciontGuisClient::handleAction));
        } else if ("view_recipe_netherite_helmet".equals(action)) {
            client.setScreen(new DynamicGuiScreen(getRecipeOverlaySmithing(), ViciontGuisClient::handleAction));
        } else if ("view_recipe_runic_chestplate".equals(action)) {
            client.setScreen(new DynamicGuiScreen(getRecipeOverlayRunicTable(), ViciontGuisClient::handleAction));

            // --- EVENTOS DE LA GUI DE CAMBIOS ---
        } else if (action.startsWith("view_cambio_")) {
            int n = Integer.parseInt(action.substring("view_cambio_".length()));
            client.setScreen(new DynamicGuiScreen(getCambioLecturaJson(n), ViciontGuisClient::handleAction));
        } else if (action.startsWith("toggle_cambio_")) {
            int n = Integer.parseInt(action.substring("toggle_cambio_".length()));
            boolean actual = cambiosLeidos.getOrDefault(n, false);
            cambiosLeidos.put(n, !actual);
            // Volvemos a abrir la misma pantalla de lectura para refrescar el visto
            client.setScreen(new DynamicGuiScreen(getCambioLecturaJson(n), ViciontGuisClient::handleAction));

        } else if (action.startsWith("view_recipe_")) {
            System.out.println("Receta aún no implementada en el JSON de prueba: " + action);
        }
    }

    private void openMainMenu(MinecraftClient client) {
        client.setScreen(new DynamicGuiScreen(getMainMenuJson(), ViciontGuisClient::handleAction));
    }

    private static String getMainMenuJson() {
        return """
            {
              "gui_id": "menu_principal",
              "elements": [
                {
                  "id": "centro_logo", "type": "image", "texture": "viciontguis:textures/gui/center.png",
                  "x": 0, "y": 0, "width": 120, "height": 120
                },
                {
                  "id": "btn_misiones", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones.png",
                  "action": "open_misiones",
                  "x": 0, "y": -110, "width": 64, "height": 64,
                  "tooltip": [{"text": "Misiones", "color": "#A349A4"}]
                },
                {
                  "id": "btn_entidades", "type": "custom_button", "texture": "viciontguis:textures/gui/entidades.png",
                  "action": "open_entidades",
                  "x": 0, "y": 110, "width": 64, "height": 64,
                  "tooltip": [{"text": "Entidades", "color": "#ED1C24"}]
                },
                {
                  "id": "btn_cambios", "type": "custom_button", "texture": "viciontguis:textures/gui/cambios.png",
                  "action": "open_cambios",
                  "x": -110, "y": 0, "width": 64, "height": 64,
                  "tooltip": [{"text": "Cambios", "color": "#FFC90E"}]
                },
                {
                  "id": "btn_recetas", "type": "custom_button", "texture": "viciontguis:textures/gui/recetas.png",
                  "action": "open_recetas",
                  "x": 110, "y": 0, "width": 64, "height": 64,
                  "tooltip": [{"text": "Recetas", "color": "#22B14C"}]
                },
                {
                  "id": "btn_fiesta_logros", "type": "custom_button", "texture": "viciontguis:textures/gui/fiestalogros.png",
                  "x": -80, "y": 90, "width": 50, "height": 50,
                  "tooltip": [{"text": "Fiesta de Logros", "color": "#FFAEC9"}]
                },
                {
                  "id": "btn_fiesta_crafteos", "type": "custom_button", "texture": "viciontguis:textures/gui/fiestacrafteos.png",
                  "x": 80, "y": 90, "width": 50, "height": 50,
                  "tooltip": [{"text": "Fiesta de Crafteos", "color": "#B97A57"}]
                }
              ]
            }
            """;
    }

    // ==========================================
    // GUI DE CAMBIOS (grid de 16: 4 filas x 4)
    // ==========================================
    private static String getCambiosJson() {
        List<String> elements = new ArrayList<>();

        elements.add("{ \"id\": \"btn_back\", \"type\": \"custom_button\", \"texture\": \"viciontguis:textures/gui/flecha_menu_anterior.png\", \"action\": \"open_main\", \"x\": -136, \"y\": -105, \"width\": 35, \"height\": 36, \"tooltip\": [{\"text\": \"Volver al Menú Principal\", \"color\": \"#FFFFFF\"}] }");
        // Flechas prev/next bajadas 10 puntos (100 -> 110)
        elements.add("{ \"id\": \"btn_prev\", \"type\": \"custom_button\", \"texture\": \"viciontguis:textures/gui/flecha_izq.png\", \"action\": \"prev_page\", \"x\": -49, \"y\": 122, \"width\": 35, \"height\": 36 }");
        elements.add("{ \"id\": \"btn_next\", \"type\": \"custom_button\", \"texture\": \"viciontguis:textures/gui/flecha_der.png\", \"action\": \"next_page\", \"x\": 49, \"y\": 122, \"width\": 35, \"height\": 36 }");

        int[] colX = { -124, -41, 41, 124 };
        // Fila 1 bajada 5 puntos (-45 -> -40); resto de filas con el mismo espaciado (38) para las 4 filas
        int[] rowY = { -44, -2, 40, 82 };

        int n = 1;
        for (int row = 0; row < CAMBIOS_ROWS; row++) {
            for (int col = 0; col < CAMBIOS_COLS; col++) {
                boolean leido = cambiosLeidos.getOrDefault(n, false);
                String tex = leido
                        ? "viciontguis:textures/gui/cambio_leido.png"
                        : "viciontguis:textures/gui/cambio_no_leido.png";
                int x = colX[col];
                int y = rowY[row];

                elements.add("{ \"id\": \"c" + n + "\", \"type\": \"custom_button\", \"texture\": \"" + tex + "\", \"action\": \"view_cambio_" + n + "\", \"x\": " + x + ", \"y\": " + y + ", \"width\": 70, \"height\": 31 }");
                elements.add("{ \"id\": \"c" + n + "_label\", \"type\": \"text\", \"text\": \"Cambio #" + n + "\", \"x\": " + x + ", \"y\": " + (y - 3) + ", \"color\": \"#FFF3D6\", \"scale\": 0.66, \"bold\": true }");

                n++;
            }
        }

        String elementsJson = String.join(",\n", elements);

        return "{ \"gui_id\": \"menu_cambios\", \"background\": { \"texture\": \"viciontguis:textures/gui/fondo_cambios.png\", \"width\": 370, \"height\": 304 }, \"elements\": [ "
                + elementsJson + " ] }";
    }

    // ==========================================
    // GUI DE LECTURA DE UN CAMBIO INDIVIDUAL
    // ==========================================
    private static String getCambioLecturaJson(int n) {
        String mensaje = cambiosMensajes.getOrDefault(n,
                "[\"\",{\"text\":\"Todavia no hay informacion sobre este cambio.\",\"color\":\"gray\"}]");
        boolean leido = cambiosLeidos.getOrDefault(n, false);

        List<String> elements = new ArrayList<>();

        // Click en cualquier parte fuera de los botones cierra y vuelve al grid (mismo patrón que las recetas)
        elements.add("{ \"id\": \"bg_close\", \"type\": \"invisible_button\", \"action\": \"open_cambios\", \"x\": 0, \"y\": 0, \"width\": 2000, \"height\": 2000 }");

        elements.add("{ \"id\": \"titulo\", \"type\": \"text\", \"text\": \"Cambio #" + n + "\", \"x\": 0, \"y\": -100, \"color\": \"#FFFFFF\", \"scale\": 1.6, \"bold\": true }");

        // max_height limita el alto disponible para el mensaje; si el texto no entra, se achica solo.
        elements.add("{ \"id\": \"mensaje\", \"type\": \"rich_text\", \"message\": " + mensaje + ", \"x\": -150, \"y\": -60, \"max_width\": 300, \"max_height\": 140, \"color\": \"#FFFFFF\" }");

        elements.add("{ \"id\": \"btn_confirmar\", \"type\": \"custom_button\", \"texture\": \"viciontguis:textures/gui/confirmar_lectura.png\", \"action\": \"toggle_cambio_" + n + "\", \"x\": 0, \"y\": 95, \"width\": 32, \"height\": 32, \"tooltip\": [{\"text\": \"Marcar como leído\", \"color\": \"#FFFFFF\"}] }");

        if (leido) {
            elements.add("{ \"id\": \"visto\", \"type\": \"text\", \"text\": \"\u2714\", \"x\": 0, \"y\": 88, \"color\": \"#55FF55\", \"scale\": 1.4, \"bold\": true }");
        }

        // Subido de y=120 a y=112 para que no choque con el borde inferior del fondo.
        elements.add("{ \"id\": \"hint\", \"type\": \"text\", \"text\": \"Dale click para confirmar lectura\", \"x\": 0, \"y\": 112, \"color\": \"#AAAAAA\", \"scale\": 0.7 }");

        String elementsJson = String.join(",\n", elements);

        return "{ \"gui_id\": \"overlay_cambio_lectura\", \"background\": { \"texture\": \"viciontguis:textures/gui/fondo_cambios_lectura.png\", \"width\": 340, \"height\": 260 }, \"elements\": [ "
                + elementsJson + " ] }";
    }

    private static String getEntidadesJson() {
        return """
        {
          "gui_id": "menu_entidades",
          "background": {
            "texture": "viciontguis:textures/gui/fondo_entidades.png",
            "width": 370,
            "height": 290
          },
          "elements": [
            {
              "id": "btn_back", "type": "custom_button", "texture": "viciontguis:textures/gui/flecha_menu_anterior.png",
              "action": "open_main", "x": -136, "y": -106, "width": 35, "height": 36,
              "tooltip": [{"text": "Volver al Menú Principal", "color": "#FFFFFF"}]
            },
            {
              "id": "btn_prev", "type": "custom_button", "texture": "viciontguis:textures/gui/flecha_izq.png",
              "action": "prev_page", "x": -49, "y": 125, "width": 35, "height": 36
            },
            {
              "id": "btn_next", "type": "custom_button", "texture": "viciontguis:textures/gui/flecha_der.png",
              "action": "next_page", "x": 49, "y": 125, "width": 35, "height": 36
            },

            { "id": "e1", "type": "entity", "entity_id": "minecraft:zombie", "entity_name": "Corrupted Zombie", "entity_scale": 24, "texture": "viciontguis:textures/gui/entidad_descubierta.png", "x": -126, "y": -20, "width": 58, "height": 81,
              "tooltip": [
                {"text": "Corrupted Zombie", "color": "#550055", "bold": true},
                {"text": ""},
                {"text": "Daño de ataque: 3", "color": "#AAAAAA"},
                {"text": "Vida: 20", "color": "#AAAAAA"},
                {"text": ""},
                {"text": "Este mob tiene la capacidad de lanzar", "color": "#FFFFFF"},
                {"text": "wind charg", "color": "#FFFFFF"},
                {"text": ""},
                {"text": "Al impactar con una entidad da Veneno I y Debilidad I", "color": "#FFFFFF"}
              ]
            },
            { "id": "e2", "type": "entity", "entity_id": "minecraft:spider", "entity_name": "Corrupted Spider", "entity_scale": 22, "texture": "viciontguis:textures/gui/entidad_descubierta.png", "x": -63, "y": -20, "width": 58, "height": 81,
              "tooltip": [
                {"text": "Corrupted Spider", "color": "#550055", "bold": true},
                {"text": ""},
                {"text": "Daño de ataque: 3", "color": "#AAAAAA"},
                {"text": "Vida: 20", "color": "#AAAAAA"},
                {"text": ""},
                {"text": "Esta araña al atacar a un jugador le genera", "color": "#FFFFFF"},
                {"text": "una telaraña en sus pies, se puede evitar usando", "color": "#FFFFFF"},
                {"text": "un escudo", "color": "#FFFFFF"}
              ]
            },
            { "id": "e3", "type": "entity", "entity_id": "minecraft:endermite", "entity_name": "Corrupted Insect", "entity_scale": 41, "texture": "viciontguis:textures/gui/entidad_descubierta.png", "x": 0, "y": -20, "width": 58, "height": 81,
              "tooltip": [
                {"text": "Corrupted Insect", "color": "#550055", "bold": true},
                {"text": ""},
                {"text": "Daño de ataque: 3", "color": "#AAAAAA"},
                {"text": "Vida: 25", "color": "#AAAAAA"},
                {"text": ""},
                {"text": "Este mob de vez en cuando lanza unas esporas", "color": "#FFFFFF"},
                {"text": "en área que deja veneno y te tapa la pantalla.", "color": "#FFFFFF"}
              ]
            },
            { "id": "e4", "type": "custom_button", "texture": "viciontguis:textures/gui/entidad_no_descubierta.png", "x": 63, "y": -20, "width": 58, "height": 81,
              "tooltip": [{"text": "Entidad no descubierta.", "color": "#A0A0A0"}]
            },
            { "id": "e5", "type": "custom_button", "texture": "viciontguis:textures/gui/entidad_no_descubierta.png", "x": 126, "y": -20, "width": 58, "height": 81,
              "tooltip": [{"text": "Entidad no descubierta.", "color": "#A0A0A0"}]
            },

            { "id": "e6", "type": "custom_button", "texture": "viciontguis:textures/gui/entidad_no_descubierta.png", "x": -126, "y": 69, "width": 58, "height": 81, "tooltip": [{"text": "Entidad no descubierta.", "color": "#A0A0A0"}] },
            { "id": "e7", "type": "custom_button", "texture": "viciontguis:textures/gui/entidad_no_descubierta.png", "x": -63, "y": 69, "width": 58, "height": 81, "tooltip": [{"text": "Entidad no descubierta.", "color": "#A0A0A0"}] },
            { "id": "e8", "type": "custom_button", "texture": "viciontguis:textures/gui/entidad_no_descubierta.png", "x": 0, "y": 69, "width": 58, "height": 81, "tooltip": [{"text": "Entidad no descubierta.", "color": "#A0A0A0"}] },
            { "id": "e9", "type": "custom_button", "texture": "viciontguis:textures/gui/entidad_no_descubierta.png", "x": 63, "y": 69, "width": 58, "height": 81, "tooltip": [{"text": "Entidad no descubierta.", "color": "#A0A0A0"}] },
            { "id": "e10", "type": "custom_button", "texture": "viciontguis:textures/gui/entidad_no_descubierta.png", "x": 126, "y": 69, "width": 58, "height": 81, "tooltip": [{"text": "Entidad no descubierta.", "color": "#A0A0A0"}] }
          ]
        }
        """;
    }

    private static String getRecetasJson() {
        return """
            {
              "gui_id": "menu_recetas",
              "background": {
                "texture": "viciontguis:textures/gui/fondo_recetas.png", "width": 370, "height": 290
              },
              "elements": [
                {
                  "id": "btn_back", "type": "custom_button", "texture": "viciontguis:textures/gui/flecha_menu_anterior.png",
                  "action": "open_main", "x": -136, "y": -106, "width": 35, "height": 36,
                  "tooltip": [{"text": "Volver al Menú Principal", "color": "#FFFFFF"}]
                },
                { "id": "btn_prev", "type": "custom_button", "texture": "viciontguis:textures/gui/flecha_izq.png", "action": "prev_page", "x": -49, "y": 117, "width": 35, "height": 36 },
                { "id": "btn_next", "type": "custom_button", "texture": "viciontguis:textures/gui/flecha_der.png", "action": "next_page", "x": 49, "y": 117, "width": 35, "height": 36 },
                
                { "id": "r1", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:cooked_beef", "custom_model_data": 2, "x": -109, "y": -28, "width": 35, "height": 36, "action": "view_recipe_corrupted_steak",
                  "tooltip": [ {"text": "Carne Corrupta", "color": "#AA00AA", "bold": true} ]
                },
                { "id": "r2", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:netherite_scrap", "custom_model_data": 5, "x": -65, "y": -28, "width": 35, "height": 36, "action": "view_recipe_corrupted_scrap",
                  "tooltip": [ {"text": "Corrupted Netherite Scrap", "color": "#AA00AA", "bold": true} ]
                },
                { "id": "r3", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:netherite_helmet", "x": -22, "y": -28, "width": 35, "height": 36, "action": "view_recipe_netherite_helmet",
                  "tooltip": [ {"text": "Netherite Helmet", "color": "#FFFFFF"} ]
                },
                { "id": "r4", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:netherite_chestplate", "custom_model_data": 2, "x": 22, "y": -28, "width": 35, "height": 36, "action": "view_recipe_runic_chestplate",
                  "tooltip": [ {"text": "Corrupted Netherite Chestplate", "color": "#9966ff", "bold": true} ]
                },
                
                { "id": "r5", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:echo_shard", "custom_model_data": 100, "x": 65, "y": -28, "width": 35, "height": 36, "tooltip": [ {"text": "Upgrade Vacío", "color": "#AAAAAA", "bold": true} ] },
                { "id": "r6", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:echo_shard", "custom_model_data": 50, "x": 109, "y": -28, "width": 35, "height": 36, "tooltip": [ {"text": "Nether Emblem", "color": "#FFAA00", "bold": true} ] },
                
                { "id": "r7", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:iron_nugget", "custom_model_data": 15, "x": -109, "y": 22, "width": 35, "height": 36, "tooltip": [ {"text": "Fragmento Infernal", "color": "#FF5555", "bold": true} ] },
                { "id": "r8", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:yellow_dye", "custom_model_data": 2, "x": -65, "y": 22, "width": 35, "height": 36, "tooltip": [ {"text": "Aguijón Real", "color": "#FFAA00", "bold": true} ] },
                { "id": "r9", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:nether_star", "custom_model_data": 5, "x": -22, "y": 22, "width": 35, "height": 36, "tooltip": [ {"text": "Corrupted Nether Star", "color": "#890bae", "bold": true} ] },
                { "id": "r10", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:echo_shard", "custom_model_data": 305, "x": 22, "y": 22, "width": 35, "height": 36, "tooltip": [ {"text": "Chestplate Netherite Upgrade", "color": "#cc3366", "bold": true} ] },
                { "id": "r11", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:ancient_debris", "custom_model_data": 5, "x": 65, "y": 22, "width": 35, "height": 36, "tooltip": [ {"text": "Corrupted Ancient Debris", "color": "#990066", "bold": true} ] },
                { "id": "r12", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:netherite_ingot", "custom_model_data": 5, "x": 109, "y": 22, "width": 35, "height": 36, "tooltip": [ {"text": "Corrupted Netherite Ingot", "color": "#9900cc", "bold": true} ] },
                
                { "id": "r13", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:air", "x": -109, "y": 72, "width": 35, "height": 36 },
                { "id": "r14", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:air", "x": -65, "y": 72, "width": 35, "height": 36 },
                { "id": "r15", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:air", "x": -22, "y": 72, "width": 35, "height": 36 },
                { "id": "r16", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:air", "x": 22, "y": 72, "width": 35, "height": 36 },
                { "id": "r17", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:air", "x": 65, "y": 72, "width": 35, "height": 36 },
                { "id": "r18", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:air", "x": 109, "y": 72, "width": 35, "height": 36 }
              ]
            }
            """;
    }

    private static String getRecipeOverlayCraftingTable() {
        return """
    {
      "gui_id": "overlay_crafting",
      "background": {
        "texture": "viciontguis:textures/gui/fondo_crafteos.png", "width": 340, "height": 260
      },
      "elements": [
        { "id": "bg_close", "type": "invisible_button", "action": "open_recetas", "x": 0, "y": 0, "width": 2000, "height": 2000 },
        { "id": "titulo", "type": "text", "text": "Mesa de Crafteo", "x": 0, "y": -110, "color": "#FFFFFF", "scale": 1.6, "bold": true },
        { "id": "i1", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:rotten_flesh", "custom_model_data": 2, "x": -88, "y": -30, "width": 32, "height": 32, "tooltip": [{"text": "Corrupted Meat", "color": "#AAAAAA"}] },
        { "id": "i2", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:rotten_flesh", "custom_model_data": 2, "x": -52, "y": -30, "width": 32, "height": 32 },
        { "id": "i3", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:rotten_flesh", "custom_model_data": 2, "x": -16, "y": -30, "width": 32, "height": 32 },
        { "id": "i4", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:rotten_flesh", "custom_model_data": 2, "x": -88, "y": 6, "width": 32, "height": 32 },
        { "id": "i5", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:cooked_beef", "x": -52, "y": 6, "width": 32, "height": 32, "tooltip": [{"text": "Cooked Beef", "color": "#FFFFFF"}] },
        { "id": "i6", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:rotten_flesh", "custom_model_data": 2, "x": -16, "y": 6, "width": 32, "height": 32 },
        { "id": "i7", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:rotten_flesh", "custom_model_data": 2, "x": -88, "y": 42, "width": 32, "height": 32 },
        { "id": "i8", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:rotten_flesh", "custom_model_data": 2, "x": -52, "y": 42, "width": 32, "height": 32 },
        { "id": "i9", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:rotten_flesh", "custom_model_data": 2, "x": -16, "y": 42, "width": 32, "height": 32 },
        { "id": "arrow", "type": "image", "texture": "viciontguis:textures/gui/flecha_der.png", "x": 32, "y": 6, "width": 24, "height": 24 },
        { "id": "res", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:cooked_beef", "custom_model_data": 2, "x": 84, "y": 6, "width": 40, "height": 40, "tooltip": [{"text": "Carne Corrupta", "color": "#AA00AA", "bold": true}] }
      ]
    }
    """;
    }

    private static String getRecipeOverlayFurnace() {
        return """
        {
          "gui_id": "overlay_furnace",
          "background": {
            "texture": "viciontguis:textures/gui/fondo_crafteos.png", "width": 340, "height": 260
          },
          "elements": [
            { "id": "bg_close", "type": "invisible_button", "action": "open_recetas", "x": 0, "y": 0, "width": 2000, "height": 2000 },
            { "id": "titulo", "type": "text", "text": "Horno", "x": 0, "y": -110, "color": "#FFFFFF", "scale": 1.6, "bold": true },
            { "id": "input", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:ancient_debris", "custom_model_data": 5, "x": -60, "y": -16, "width": 32, "height": 32, "tooltip": [{"text": "Corrupted Ancient Debris", "color": "#990066", "bold": true}] },
            { "id": "fuel", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:coal", "x": -60, "y": 32, "width": 32, "height": 32, "tooltip": [{"text": "Coal", "color": "#FFFFFF"}] },
            { "id": "arrow", "type": "image", "texture": "viciontguis:textures/gui/flecha_der.png", "x": 0, "y": 8, "width": 24, "height": 24 },
            { "id": "res", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:netherite_scrap", "custom_model_data": 5, "x": 60, "y": 8, "width": 40, "height": 40, "tooltip": [{"text": "Corrupted Netherite Scrap", "color": "#AA00AA", "bold": true}] }
          ]
        }
        """;
    }

    private static String getRecipeOverlaySmithing() {
        return """
        {
          "gui_id": "overlay_smithing",
          "background": {
            "texture": "viciontguis:textures/gui/fondo_crafteos.png", "width": 340, "height": 260
          },
          "elements": [
            { "id": "bg_close", "type": "invisible_button", "action": "open_recetas", "x": 0, "y": 0, "width": 2000, "height": 2000 },
            { "id": "titulo", "type": "text", "text": "Mesa de Herreria", "x": 0, "y": -110, "color": "#FFFFFF", "scale": 1.6, "bold": true },
            { "id": "template", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:netherite_upgrade_smithing_template", "x": -90, "y": 8, "width": 32, "height": 32, "tooltip": [{"text": "Netherite Upgrade", "color": "#FFFFFF"}] },
            { "id": "armor", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:diamond_helmet", "x": -50, "y": 8, "width": 32, "height": 32, "tooltip": [{"text": "Diamond Helmet", "color": "#FFFFFF"}] },
            { "id": "ingot", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:netherite_ingot", "x": -10, "y": 8, "width": 32, "height": 32, "tooltip": [{"text": "Netherite Ingot", "color": "#FFFFFF"}] },
            { "id": "arrow", "type": "image", "texture": "viciontguis:textures/gui/flecha_der.png", "x": 40, "y": 8, "width": 24, "height": 24 },
            { "id": "res", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:netherite_helmet", "x": 90, "y": 8, "width": 40, "height": 40, "tooltip": [{"text": "Netherite Helmet", "color": "#FFFFFF"}] }
          ]
        }
        """;
    }

    private static String getRecipeOverlayRunicTable() {
        return """
        {
          "gui_id": "overlay_runic",
          "background": {
            "texture": "viciontguis:textures/gui/fondo_crafteos.png", "width": 340, "height": 260
          },
          "elements": [
            { "id": "bg_close", "type": "invisible_button", "action": "open_recetas", "x": 0, "y": 0, "width": 2000, "height": 2000 },
            { "id": "titulo", "type": "text", "text": "Mesa Runica", "x": 0, "y": -110, "color": "#FFFFFF", "scale": 1.6, "bold": true },
            { "id": "slot_1", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:netherite_chestplate", "x": -120, "y": 8, "width": 32, "height": 32, "tooltip": [{"text": "Netherite Chestplate", "color": "#FFFFFF"}] },
            { "id": "slot_3", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:echo_shard", "custom_model_data": 305, "x": -80, "y": 8, "width": 32, "height": 32, "tooltip": [{"text": "Chestplate Netherite Upgrade", "color": "#cc3366", "bold": true}] },
            { "id": "slot_5", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:echo_shard", "x": -40, "y": 8, "width": 32, "height": 32, "tooltip": [{"text": "Echo Shard", "color": "#FFFFFF"}] },
            { "id": "slot_7", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:netherite_ingot", "custom_model_data": 5, "x": 0, "y": 8, "width": 32, "height": 32, "tooltip": [{"text": "Corrupted Netherite Ingot", "color": "#9900cc", "bold": true}] },
            { "id": "arrow", "type": "image", "texture": "viciontguis:textures/gui/flecha_der.png", "x": 40, "y": 8, "width": 24, "height": 24 },
            { "id": "res", "type": "item_slot", "texture": "viciontguis:textures/gui/marco_item.png", "item_id": "minecraft:netherite_chestplate", "custom_model_data": 2, "x": 90, "y": 8, "width": 40, "height": 40, "tooltip": [{"text": "Corrupted Netherite Chestplate", "color": "#9966ff", "bold": true}] }
          ]
        }
        """;
    }

    private static String getMisionesJson() {
        return """
            {
              "gui_id": "menu_misiones",
              "background": {
                "texture": "viciontguis:textures/gui/fondo_misiones.png",
                "width": 370,
                "height": 290
              },
              "elements": [
                {
                  "id": "btn_back", "type": "custom_button", "texture": "viciontguis:textures/gui/flecha_menu_anterior.png",
                  "action": "open_main", "x": -136, "y": -106, "width": 35, "height": 36,
                  "tooltip": [{"text": "Volver al Menú Principal", "color": "#FFFFFF"}]
                },
                {
                  "id": "btn_prev", "type": "custom_button", "texture": "viciontguis:textures/gui/flecha_izq.png",
                  "action": "prev_page", "x": -49, "y": 117, "width": 35, "height": 36
                },
                {
                  "id": "btn_next", "type": "custom_button", "texture": "viciontguis:textures/gui/flecha_der.png",
                  "action": "next_page", "x": 49, "y": 117, "width": 35, "height": 36
                },
                
                { "id": "m1", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_completadas.png", "x": -109, "y": -22, "width": 35, "height": 36,
                  "tooltip": [
                    {"text": "Misión 1", "color": "#90EE90"}, {"text": "Consigue la Armadura", "color": "#D3D3D3"},
                    {"text": ""}, {"text": "✔ Completada", "color": "#98FB98"},
                    {"text": ""}, {"text": "Progreso de armadura:", "color": "#F0E68C"},
                    {"text": "- Casco de Diamante", "color": "#98FB98"}
                  ]
                },
                { "id": "m2", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_completadas.png", "x": -54, "y": -22, "width": 35, "height": 36,
                  "tooltip": [
                    {"text": "Misión 2", "color": "#90EE90"}, {"text": "Encanta la armadura", "color": "#D3D3D3"},
                    {"text": ""}, {"text": "✔ Completada", "color": "#98FB98"}
                  ]
                },
                { "id": "m3", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_no_completadas.png", "x": 0, "y": -22, "width": 35, "height": 36,
                  "tooltip": [
                    {"text": "Misión 6", "color": "#FFB6C1"}, {"text": "Mata Entidades Corruptas", "color": "#D3D3D3"},
                    {"text": ""}, {"text": "✖ Pendiente", "color": "#FFA07A"},
                    {"text": ""}, {"text": "Progreso de eliminaciones:", "color": "#F0E68C"},
                    {"text": "- Corrupted Zombies: 10/25", "color": "#DDA0DD"}
                  ]
                },
                { "id": "m4", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_no_completadas.png", "x": 54, "y": -22, "width": 35, "height": 36,
                  "tooltip": [
                    {"text": "Misión 12", "color": "#FFB6C1"}, {"text": "Busca Bombitas", "color": "#D3D3D3"},
                    {"text": ""}, {"text": "✖ Pendiente", "color": "#FFA07A"},
                    {"text": ""}, {"text": "Progreso de eliminaciones:", "color": "#F0E68C"},
                    {"text": "- Bombitas: 0/30", "color": "#DDA0DD"}
                  ]
                },
                { "id": "m5", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_no_descubiertas.png", "x": 109, "y": -22, "width": 35, "height": 36,
                  "tooltip": [{"text": "???", "color": "#A0A0A0"}, {"text": "Misión no descubierta", "color": "#D3D3D3"}]
                },
                
                { "id": "m6", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_no_descubiertas.png", "x": -109, "y": 28, "width": 35, "height": 36, "tooltip": [{"text": "???", "color": "#A0A0A0"}, {"text": "Misión no descubierta", "color": "#D3D3D3"}] },
                { "id": "m7", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_no_descubiertas.png", "x": -54, "y": 28, "width": 35, "height": 36, "tooltip": [{"text": "???", "color": "#A0A0A0"}, {"text": "Misión no descubierta", "color": "#D3D3D3"}] },
                { "id": "m8", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_no_descubiertas.png", "x": 0, "y": 28, "width": 35, "height": 36, "tooltip": [{"text": "???", "color": "#A0A0A0"}, {"text": "Misión no descubierta", "color": "#D3D3D3"}] },
                { "id": "m9", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_no_descubiertas.png", "x": 54, "y": 28, "width": 35, "height": 36, "tooltip": [{"text": "???", "color": "#A0A0A0"}, {"text": "Misión no descubierta", "color": "#D3D3D3"}] },
                { "id": "m10", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_no_descubiertas.png", "x": 109, "y": 28, "width": 35, "height": 36, "tooltip": [{"text": "???", "color": "#A0A0A0"}, {"text": "Misión no descubierta", "color": "#D3D3D3"}] },
                
                { "id": "m11", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_no_descubiertas.png", "x": -109, "y": 78, "width": 35, "height": 36, "tooltip": [{"text": "???", "color": "#A0A0A0"}, {"text": "Misión no descubierta", "color": "#D3D3D3"}] },
                { "id": "m12", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_no_descubiertas.png", "x": -54, "y": 78, "width": 35, "height": 36, "tooltip": [{"text": "???", "color": "#A0A0A0"}, {"text": "Misión no descubierta", "color": "#D3D3D3"}] },
                { "id": "m13", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_no_descubiertas.png", "x": 0, "y": 78, "width": 35, "height": 36, "tooltip": [{"text": "???", "color": "#A0A0A0"}, {"text": "Misión no descubierta", "color": "#D3D3D3"}] },
                { "id": "m14", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_no_descubiertas.png", "x": 54, "y": 78, "width": 35, "height": 36, "tooltip": [{"text": "???", "color": "#A0A0A0"}, {"text": "Misión no descubierta", "color": "#D3D3D3"}] },
                { "id": "m15", "type": "custom_button", "texture": "viciontguis:textures/gui/misiones_no_descubiertas.png", "x": 109, "y": 78, "width": 35, "height": 36, "tooltip": [{"text": "???", "color": "#A0A0A0"}, {"text": "Misión no descubierta", "color": "#D3D3D3"}] }
              ]
            }
            """;
    }
}