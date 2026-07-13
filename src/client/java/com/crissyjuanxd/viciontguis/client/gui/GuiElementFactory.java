package com.crissyjuanxd.viciontguis.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsea el JSON que manda el plugin (o el JSON de prueba local) a un modelo
 * de datos ({@link GuiBackground} + lista de {@link GuiElement}) que
 * {@link com.crissyjuanxd.viciontguis.client.gui.DynamicGuiScreen} puede renderizar.
 * <p>
 * Esta clase no dibuja nada ni conoce Screen/DrawContext: solo transforma JSON -> datos.
 */
public final class GuiElementFactory {

    private GuiElementFactory() {}

    public record ParseResult(GuiBackground background, List<GuiElement> elements) {}

    public static ParseResult parse(JsonObject guiData, int centerX, int centerY, TextRenderer textRenderer) {
        GuiBackground background = null;
        if (guiData.has("background")) {
            JsonObject bg = guiData.getAsJsonObject("background");
            Identifier texture = Identifier.of(bg.get("texture").getAsString());
            int width = bg.get("width").getAsInt();
            int height = bg.get("height").getAsInt();
            int texWidth = bg.has("texture_width") ? bg.get("texture_width").getAsInt() : width;
            int texHeight = bg.has("texture_height") ? bg.get("texture_height").getAsInt() : height;
            background = new GuiBackground(texture, width, height, texWidth, texHeight);

            // Textura conocida de antemano: la precargamos para que no lagee al abrir.
            GuiTexturePreloader.remember(texture);
        }

        List<GuiElement> elements = new ArrayList<>();
        if (guiData.has("elements")) {
            JsonArray elementsJson = guiData.getAsJsonArray("elements");
            for (JsonElement elem : elementsJson) {
                elements.add(parseElement(elem.getAsJsonObject(), centerX, centerY, textRenderer));
            }
        }

        return new ParseResult(background, elements);
    }

    private static GuiElement parseElement(JsonObject obj, int centerX, int centerY, TextRenderer textRenderer) {
        String type = obj.get("type").getAsString();
        String id = obj.get("id").getAsString();

        Identifier texture = null;
        ItemStack mcItem = null;
        String entityId = null;
        String entityName = null;
        int entityScale = 30;

        if (type.equals("item_slot") || type.equals("custom_button") || type.equals("image") || type.equals("entity")) {
            if (obj.has("texture")) {
                texture = Identifier.of(obj.get("texture").getAsString());
                GuiTexturePreloader.remember(texture);
            }
            if (type.equals("item_slot") && obj.has("item_id")) {
                String itemIdStr = obj.get("item_id").getAsString();
                Item item = Registries.ITEM.get(Identifier.of(itemIdStr));
                mcItem = new ItemStack(item);

                if (obj.has("custom_model_data")) {
                    int cmd = obj.get("custom_model_data").getAsInt();
                    mcItem.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(cmd));
                }
            }
            if (type.equals("entity") && obj.has("entity_id")) {
                entityId = obj.get("entity_id").getAsString();
                if (obj.has("entity_scale")) {
                    entityScale = obj.get("entity_scale").getAsInt();
                }
                if (obj.has("entity_name")) {
                    entityName = obj.get("entity_name").getAsString();
                }
            }
        }

        int width = obj.has("width") ? obj.get("width").getAsInt() : 0;
        int height = obj.has("height") ? obj.get("height").getAsInt() : 0;
        int x = centerX + obj.get("x").getAsInt() - (width / 2);
        int y = centerY + obj.get("y").getAsInt() - (height / 2);

        int texWidth = obj.has("texture_width") ? obj.get("texture_width").getAsInt() : width;
        int texHeight = obj.has("texture_height") ? obj.get("texture_height").getAsInt() : height;

        boolean isButton = type.equals("custom_button") || type.equals("item_slot") || type.equals("invisible_button") || type.equals("entity");
        String action = obj.has("action") ? obj.get("action").getAsString() : null;

        List<Text> tooltipLines = parseTooltip(obj);

        String textContent = null;
        int textColor = 0xFFFFFF;
        float textScale = 1.5f;
        boolean textBold = false;
        if (type.equals("text")) {
            textContent = obj.has("text") ? obj.get("text").getAsString() : "";
            if (obj.has("color")) {
                try {
                    textColor = TextColor.parse(obj.get("color").getAsString()).getOrThrow().getRgb();
                } catch (Exception ignored) {}
            }
            if (obj.has("scale")) {
                textScale = obj.get("scale").getAsFloat();
            }
            textBold = obj.has("bold") && obj.get("bold").getAsBoolean();
        }

        List<OrderedText> richLines = null;
        int richColor = 0xFFFFFF;
        float richScale = 1.0f;
        if (type.equals("rich_text") && obj.has("message")) {
            if (obj.has("color")) {
                try {
                    richColor = TextColor.parse(obj.get("color").getAsString()).getOrThrow().getRgb();
                } catch (Exception ignored) {}
            }
            try {
                int maxWidth = obj.has("max_width") ? obj.get("max_width").getAsInt() : 300;
                Integer maxHeight = obj.has("max_height") ? obj.get("max_height").getAsInt() : null;
                RichTextParser.WrappedResult wrapped = RichTextParser.wrapAndScale(textRenderer, obj.get("message"), maxWidth, maxHeight);
                richLines = wrapped.lines();
                richScale = wrapped.scale();
            } catch (Exception e) {
                System.err.println("Error parseando rich_text '" + id + "': " + e.getMessage());
                richLines = textRenderer.wrapLines(Text.literal("Error al leer el mensaje."), 300);
            }
        }

        return new GuiElement(
                id, type, texture, mcItem, x, y, width, height, texWidth, texHeight,
                isButton, tooltipLines, action, textContent, textColor, textScale, textBold,
                entityId, entityName, entityScale, richLines, richColor, richScale
        );
    }

    private static List<Text> parseTooltip(JsonObject obj) {
        List<Text> tooltipLines = new ArrayList<>();
        if (!obj.has("tooltip")) return tooltipLines;

        JsonArray lines = obj.getAsJsonArray("tooltip");
        for (JsonElement lineElem : lines) {
            JsonObject lineObj = lineElem.getAsJsonObject();
            String txt = lineObj.has("text") ? lineObj.get("text").getAsString() : "";
            String color = lineObj.has("color") ? lineObj.get("color").getAsString() : "#FFFFFF";
            boolean bold = lineObj.has("bold") && lineObj.get("bold").getAsBoolean();

            MutableText mt = Text.literal(txt);
            try {
                mt.setStyle(mt.getStyle().withColor(TextColor.parse(color).getOrThrow()).withBold(bold));
            } catch (Exception ignored) {}
            tooltipLines.add(mt);
        }
        return tooltipLines;
    }
}