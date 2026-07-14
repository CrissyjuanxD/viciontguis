package com.crissyjuanxd.viciontguis.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

public final class GuiElementRenderer {

    private GuiElementRenderer() {}

    public static void renderItemSlot(DrawContext context, GuiElement element, int screenWidth, int screenHeight, boolean isHovered) {
        renderItemSlot(context, element, screenWidth, screenHeight, isHovered, 0, 0);
    }

    public static void renderItemSlot(DrawContext context, GuiElement element, int screenWidth, int screenHeight, boolean isHovered, int shiftX, int shiftY) {
        int rx = element.getRenderX(screenWidth, shiftX);
        int ry = element.getRenderY(screenHeight, shiftY);

        if (element.texture != null) {
            context.drawTexture(element.texture, rx, ry, 0, 0, element.width, element.height, element.texWidth, element.texHeight);
        }

        if (isHovered) {
            context.fill(rx + 2, ry + 2, rx + element.width - 2, ry + element.height - 2, 0x80FFFFFF);
        }

        if (element.mcItem != null && !element.mcItem.isEmpty()) {
            context.getMatrices().push();
            float itemScale = (element.width - 8) / 16.0f;
            context.getMatrices().translate(rx + 4, ry + 4, 100);
            context.getMatrices().scale(itemScale, itemScale, 1.0f);
            context.drawItem(element.mcItem, 0, 0);
            context.getMatrices().pop();
        }
    }

    public static void renderText(DrawContext context, TextRenderer textRenderer, GuiElement element, int screenWidth, int screenHeight) {
        renderText(context, textRenderer, element, screenWidth, screenHeight, 0, 0);
    }

    public static void renderText(DrawContext context, TextRenderer textRenderer, GuiElement element, int screenWidth, int screenHeight, int shiftX, int shiftY) {
        int rx = element.getBaseX(screenWidth) + shiftX + element.offsetX;
        int ry = element.getBaseY(screenHeight) + shiftY + element.offsetY;

        context.getMatrices().push();
        context.getMatrices().translate(rx, ry, 0);
        context.getMatrices().scale(element.textScale, element.textScale, 1.0f);

        MutableText renderText = Text.literal(element.text).setStyle(
                Text.empty().getStyle().withBold(element.textBold)
        );
        context.drawCenteredTextWithShadow(textRenderer, renderText, 0, 0, element.textColor);

        context.getMatrices().pop();
    }

    public static void renderRichText(DrawContext context, TextRenderer textRenderer, GuiElement element, int screenWidth, int screenHeight) {
        renderRichText(context, textRenderer, element, screenWidth, screenHeight, 0, 0);
    }

    public static void renderRichText(DrawContext context, TextRenderer textRenderer, GuiElement element, int screenWidth, int screenHeight, int shiftX, int shiftY) {
        if (element.richLines == null) return;

        int rx = element.getBaseX(screenWidth) + shiftX + element.offsetX;
        int ry = element.getBaseY(screenHeight) + shiftY + element.offsetY;
        int lineHeight = textRenderer.fontHeight + 2;

        context.getMatrices().push();
        context.getMatrices().translate(rx, ry, 0);
        context.getMatrices().scale(element.richScale, element.richScale, 1.0f);

        int ly = 0;
        for (OrderedText line : element.richLines) {
            context.drawText(textRenderer, line, 0, ly, element.richColor, true);
            ly += lineHeight;
        }

        context.getMatrices().pop();
    }

    public static void renderImage(DrawContext context, GuiElement element, int screenWidth, int screenHeight) {
        renderImage(context, element, screenWidth, screenHeight, 0, 0);
    }

    public static void renderImage(DrawContext context, GuiElement element, int screenWidth, int screenHeight, int shiftX, int shiftY) {
        if (element.texture == null) return;
        int rx = element.getRenderX(screenWidth, shiftX);
        int ry = element.getRenderY(screenHeight, shiftY);
        context.drawTexture(element.texture, rx, ry, 0, 0, element.width, element.height, element.texWidth, element.texHeight);
    }
}