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
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class GuiElementFactory {

    private GuiElementFactory() {}

    // NUEVO: Añadido boolean fixedScale al Record
    public record ParseResult(GuiBackground background, List<GuiElement> elements, boolean fixedScale) {}

    public static ParseResult parse(JsonObject guiData, TextRenderer textRenderer) {
        GuiBackground background = null;
        if (guiData.has("background")) {
            JsonObject bg = guiData.getAsJsonObject("background");
            Identifier texture = Identifier.of(bg.get("texture").getAsString());
            int width = bg.get("width").getAsInt();
            int height = bg.get("height").getAsInt();
            int texWidth = bg.has("texture_width") ? bg.get("texture_width").getAsInt() : width;
            int texHeight = bg.has("texture_height") ? bg.get("texture_height").getAsInt() : height;
            background = new GuiBackground(texture, width, height, texWidth, texHeight);
        }

        List<GuiElement> elements = new ArrayList<>();
        if (guiData.has("elements")) {
            JsonArray elementsJson = guiData.getAsJsonArray("elements");
            for (JsonElement elem : elementsJson) {
                elements.add(parseElement(elem.getAsJsonObject(), textRenderer));
            }
        }

        // NUEVO: Leemos del JSON si tiene la propiedad activa
        boolean fixedScale = guiData.has("fixed_scale") && guiData.get("fixed_scale").getAsBoolean();

        return new ParseResult(background, elements, fixedScale);
    }

    private static GuiElement parseElement(JsonObject obj, TextRenderer textRenderer) {
        String type = obj.get("type").getAsString();
        String id = obj.get("id").getAsString();
        String anchor = obj.has("anchor") ? obj.get("anchor").getAsString() : "center";

        Identifier texture = null;
        ItemStack mcItem = null;
        String entityId = null;
        String entityName = null;
        int entityScale = 30;

        if (type.equals("item_slot") || type.equals("custom_button") || type.equals("image") || type.equals("entity")) {
            if (obj.has("texture")) {
                texture = Identifier.of(obj.get("texture").getAsString());
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
        int offsetX = obj.has("x") ? obj.get("x").getAsInt() : 0;
        int offsetY = obj.has("y") ? obj.get("y").getAsInt() : 0;

        float animSpeed = obj.has("anim_speed") ? obj.get("anim_speed").getAsFloat() : 0f;

        int texWidth = obj.has("texture_width") ? obj.get("texture_width").getAsInt() : width;
        int texHeight = obj.has("texture_height") ? obj.get("texture_height").getAsInt() : height;

        boolean isButton = type.equals("custom_button") || type.equals("item_slot") || type.equals("invisible_button") || type.equals("entity");
        String action = obj.has("action") ? obj.get("action").getAsString() : null;

        List<Text> tooltipLines = parseTooltip(obj, mcItem);

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
        boolean richOutline = false;
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
                if (obj.has("scale")) {
                    richScale = obj.get("scale").getAsFloat();
                }
                if (obj.has("outline")) {
                    richOutline = obj.get("outline").getAsBoolean();
                }
            } catch (Exception e) {
                System.err.println("Error parseando rich_text '" + id + "': " + e.getMessage());
                richLines = textRenderer.wrapLines(Text.literal("Error al leer el mensaje."), 300);
            }
        }

        return new GuiElement(
                id, type, texture, mcItem, anchor, offsetX, offsetY, width, height, texWidth, texHeight,
                isButton, tooltipLines, action, textContent, textColor, textScale, textBold,
                entityId, entityName, entityScale, richLines, richColor, richScale, richOutline, animSpeed
        );
    }

    private static List<Text> parseTooltip(JsonObject obj, ItemStack mcItem) {
        List<Text> tooltipLines = new ArrayList<>();
        if (!obj.has("tooltip")) return tooltipLines;

        JsonArray lines = obj.getAsJsonArray("tooltip");
        for (JsonElement lineElem : lines) {
            JsonObject lineObj = lineElem.getAsJsonObject();
            String txt = lineObj.has("text") ? lineObj.get("text").getAsString() : "";
            
            if (txt.equalsIgnoreCase("default") && mcItem != null && !mcItem.isEmpty()) {
                tooltipLines.add(mcItem.getName());
                continue;
            }

            if (txt.startsWith("[") || txt.startsWith("{")) {
                try {
                    JsonElement jsonElement = com.google.gson.JsonParser.parseString(txt);
                    tooltipLines.add(RichTextParser.parse(jsonElement));
                    continue;
                } catch (Exception ignored) {}
            }

            MutableText mt = parseLegacyString(txt);

            String color = lineObj.has("color") ? lineObj.get("color").getAsString() : "#FFFFFF";
            boolean bold = lineObj.has("bold") && lineObj.get("bold").getAsBoolean();

            try {
                mt.setStyle(mt.getStyle().withColor(TextColor.parse(color).getOrThrow()).withBold(bold));
            } catch (Exception ignored) {}

            tooltipLines.add(mt);
        }
        return tooltipLines;
    }

    private static MutableText parseLegacyString(String text) {
        MutableText root = Text.empty();
        StringBuilder currentText = new StringBuilder();
        Style currentStyle = Style.EMPTY;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '§' && i + 1 < text.length()) {
                char nextChar = Character.toLowerCase(text.charAt(i + 1));

                if (nextChar == 'x' && i + 13 < text.length()) {
                    if (currentText.length() > 0) {
                        root.append(Text.literal(currentText.toString()).setStyle(currentStyle));
                        currentText.setLength(0);
                    }

                    StringBuilder hex = new StringBuilder("#");
                    hex.append(text.charAt(i+3));
                    hex.append(text.charAt(i+5));
                    hex.append(text.charAt(i+7));
                    hex.append(text.charAt(i+9));
                    hex.append(text.charAt(i+11));
                    hex.append(text.charAt(i+13));

                    try {
                        currentStyle = currentStyle.withColor(TextColor.parse(hex.toString()).getOrThrow());
                    } catch (Exception ignored) {}

                    i += 13;
                    continue;

                } else if ("0123456789abcdefklmnor".indexOf(nextChar) != -1) {
                    if (currentText.length() > 0) {
                        root.append(Text.literal(currentText.toString()).setStyle(currentStyle));
                        currentText.setLength(0);
                    }
                    switch (nextChar) {
                        case '0' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.BLACK);
                        case '1' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.DARK_BLUE);
                        case '2' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.DARK_GREEN);
                        case '3' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.DARK_AQUA);
                        case '4' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.DARK_RED);
                        case '5' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.DARK_PURPLE);
                        case '6' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.GOLD);
                        case '7' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.GRAY);
                        case '8' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.DARK_GRAY);
                        case '9' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.BLUE);
                        case 'a' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.GREEN);
                        case 'b' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.AQUA);
                        case 'c' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.RED);
                        case 'd' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.LIGHT_PURPLE);
                        case 'e' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.YELLOW);
                        case 'f' -> currentStyle = currentStyle.withColor(net.minecraft.util.Formatting.WHITE);
                        case 'k' -> currentStyle = currentStyle.withObfuscated(true);
                        case 'l' -> currentStyle = currentStyle.withBold(true);
                        case 'm' -> currentStyle = currentStyle.withStrikethrough(true);
                        case 'n' -> currentStyle = currentStyle.withUnderline(true);
                        case 'o' -> currentStyle = currentStyle.withItalic(true);
                        case 'r' -> currentStyle = Style.EMPTY;
                    }
                    i++;
                    continue;
                }
            }
            currentText.append(c);
        }

        if (currentText.length() > 0) {
            root.append(Text.literal(currentText.toString()).setStyle(currentStyle));
        }

        if (root.getSiblings().isEmpty()) {
            return Text.literal(currentText.toString());
        }
        return root;
    }
}