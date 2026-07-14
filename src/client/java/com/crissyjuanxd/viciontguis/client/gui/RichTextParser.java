package com.crissyjuanxd.viciontguis.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.List;

public final class RichTextParser {

    private RichTextParser() {}

    public static Text parse(JsonElement element) {
        if (element == null || element.isJsonNull()) return Text.literal("");
        if (element.isJsonPrimitive()) return Text.literal(element.getAsString());

        if (element.isJsonArray()) {
            MutableText result = Text.literal("");
            for (JsonElement child : element.getAsJsonArray()) {
                result.append(parse(child));
            }
            return result;
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String txt = obj.has("text") ? obj.get("text").getAsString() : "";
            MutableText mt = Text.literal(txt);

            Style style = Style.EMPTY;
            if (obj.has("color")) {
                try { style = style.withColor(TextColor.parse(obj.get("color").getAsString()).getOrThrow()); } catch (Exception ignored) {}
            }
            if (obj.has("bold")) style = style.withBold(obj.get("bold").getAsBoolean());
            if (obj.has("italic")) style = style.withItalic(obj.get("italic").getAsBoolean());
            if (obj.has("underlined")) style = style.withUnderline(obj.get("underlined").getAsBoolean());
            if (obj.has("strikethrough")) style = style.withStrikethrough(obj.get("strikethrough").getAsBoolean());
            if (obj.has("obfuscated")) style = style.withObfuscated(obj.get("obfuscated").getAsBoolean());
            mt.setStyle(style);

            if (obj.has("extra")) {
                for (JsonElement child : obj.getAsJsonArray("extra")) {
                    mt.append(parse(child));
                }
            }
            return mt;
        }
        return Text.literal("");
    }

    public record WrappedResult(List<OrderedText> lines, float scale) {}

    public static WrappedResult wrapAndScale(TextRenderer textRenderer, JsonElement message, int maxWidth, Integer maxHeight) {
        Text richText = parse(message);
        List<OrderedText> lines = textRenderer.wrapLines(richText, maxWidth);

        float scale = 1.0f;
        int lineHeight = textRenderer.fontHeight + 2;
        int totalHeight = lines.size() * lineHeight;
        if (maxHeight != null && totalHeight > maxHeight && totalHeight > 0) {
            scale = (float) maxHeight / (float) totalHeight;
            scale = Math.max(0.45f, Math.min(1.0f, scale));
        }
        return new WrappedResult(lines, scale);
    }
}