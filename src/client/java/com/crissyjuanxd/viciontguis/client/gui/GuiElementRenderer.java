package com.crissyjuanxd.viciontguis.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
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
            context.drawTexture(RenderPipelines.GUI_TEXTURED, element.texture, rx, ry, 0.0f, 0.0f, element.width, element.height, element.texWidth, element.texHeight);
        }

        if (isHovered) {
            context.fill(rx + 2, ry + 2, rx + element.width - 2, ry + element.height - 2, 0x80FFFFFF);
        }

        if (element.mcItem != null && !element.mcItem.isEmpty()) {
            context.getMatrices().pushMatrix();
            float itemScale = (element.width - 8) / 16.0f;
            context.getMatrices().translate(rx + 4, ry + 4);
            context.getMatrices().scale(itemScale, itemScale);
            context.drawItem(element.mcItem, 0, 0);
            context.getMatrices().popMatrix();
        }
    }

    public static void renderText(DrawContext context, TextRenderer textRenderer, GuiElement element, int screenWidth, int screenHeight) {
        renderText(context, textRenderer, element, screenWidth, screenHeight, 0, 0);
    }

    public static void renderText(DrawContext context, TextRenderer textRenderer, GuiElement element, int screenWidth, int screenHeight, int shiftX, int shiftY) {
        int rx = element.getBaseX(screenWidth) + shiftX + element.offsetX;
        int ry = element.getBaseY(screenHeight) + shiftY + element.offsetY;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(rx, ry);
        context.getMatrices().scale(element.textScale, element.textScale);

        MutableText renderText = Text.literal(element.text).setStyle(Text.empty().getStyle().withBold(element.textBold));

        int textWidth = textRenderer.getWidth(renderText);
        int drawX;

        if ("right".equalsIgnoreCase(element.textAlign)) {
            drawX = -textWidth;
        } else if ("left".equalsIgnoreCase(element.textAlign)) {
            drawX = 0;
        } else {
            drawX = -(textWidth / 2);
        }

        context.drawTextWithShadow(textRenderer, renderText, drawX, 0, element.textColor);
        context.getMatrices().popMatrix();
    }

    public static void renderRichText(DrawContext context, TextRenderer textRenderer, GuiElement element, int screenWidth, int screenHeight) {
        renderRichText(context, textRenderer, element, screenWidth, screenHeight, 0, 0);
    }

    public static void renderRichText(DrawContext context, TextRenderer textRenderer, GuiElement element, int screenWidth, int screenHeight, int shiftX, int shiftY) {
        if (element.richLines == null) return;

        int rx = element.getBaseX(screenWidth) + shiftX + element.offsetX;
        int ry = element.getBaseY(screenHeight) + shiftY + element.offsetY;
        int lineHeight = textRenderer.fontHeight + 2;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(rx, ry);
        context.getMatrices().scale(element.richScale, element.richScale);

        int ly = 0;

        if (element.richOutline) {
            int outlineColor = 0xFF000000;

            for (OrderedText line : element.richLines) {
                context.drawText(textRenderer, line, -1, ly, outlineColor, false);
                context.drawText(textRenderer, line, 1, ly, outlineColor, false);
                context.drawText(textRenderer, line, 0, ly - 1, outlineColor, false);
                context.drawText(textRenderer, line, 0, ly + 1, outlineColor, false);

                context.drawText(textRenderer, line, 0, ly, element.richColor, false);
                ly += lineHeight;
            }
        } else {
            for (OrderedText line : element.richLines) {
                context.drawText(textRenderer, line, 0, ly, element.richColor, true);
                ly += lineHeight;
            }
        }

        context.getMatrices().popMatrix();
    }

    public static void renderImage(DrawContext context, GuiElement element, int screenWidth, int screenHeight) {
        renderImage(context, element, screenWidth, screenHeight, 0, 0);
    }

    public static void renderImage(DrawContext context, GuiElement element, int screenWidth, int screenHeight, int shiftX, int shiftY) {
        if (element.texture == null) return;
        int rx = element.getRenderX(screenWidth, shiftX);
        int ry = element.getRenderY(screenHeight, shiftY);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, element.texture, rx, ry, 0.0f, 0.0f, element.width, element.height, element.texWidth, element.texHeight);
    }
}