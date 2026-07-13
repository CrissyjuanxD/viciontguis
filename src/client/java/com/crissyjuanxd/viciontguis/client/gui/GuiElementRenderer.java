package com.crissyjuanxd.viciontguis.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

/**
 * Dibuja los tipos de elemento que no necesitan lógica especial de mundo/entidad.
 * type="entity" se maneja aparte en {@link EntityRenderHandler} porque necesita
 * romper la matriz de transformación para el render 3D.
 */
public final class GuiElementRenderer {

    private GuiElementRenderer() {}

    public static void renderItemSlot(DrawContext context, GuiElement element, boolean isHovered) {
        if (element.texture != null) {
            context.drawTexture(element.texture, element.x, element.y, 0, 0, element.width, element.height, element.texWidth, element.texHeight);
        }

        if (isHovered) {
            context.fill(element.x + 2, element.y + 2, element.x + element.width - 2, element.y + element.height - 2, 0x80FFFFFF);
        }

        if (element.mcItem != null && !element.mcItem.isEmpty()) {
            context.getMatrices().push();
            float itemScale = (element.width - 8) / 16.0f;
            context.getMatrices().translate(element.x + 4, element.y + 4, 100);
            context.getMatrices().scale(itemScale, itemScale, 1.0f);
            context.drawItem(element.mcItem, 0, 0);
            context.getMatrices().pop();
        }
    }

    public static void renderText(DrawContext context, TextRenderer textRenderer, GuiElement element) {
        context.getMatrices().push();
        context.getMatrices().translate(element.x, element.y, 0);
        context.getMatrices().scale(element.textScale, element.textScale, 1.0f);

        MutableText renderText = Text.literal(element.text).setStyle(
                Text.empty().getStyle().withBold(element.textBold)
        );
        context.drawCenteredTextWithShadow(textRenderer, renderText, 0, 0, element.textColor);

        context.getMatrices().pop();
    }

    public static void renderRichText(DrawContext context, TextRenderer textRenderer, GuiElement element) {
        if (element.richLines == null) return;

        int lineHeight = textRenderer.fontHeight + 2;

        context.getMatrices().push();
        context.getMatrices().translate(element.x, element.y, 0);
        context.getMatrices().scale(element.richScale, element.richScale, 1.0f);

        int ly = 0;
        for (OrderedText line : element.richLines) {
            context.drawText(textRenderer, line, 0, ly, element.richColor, true);
            ly += lineHeight;
        }

        context.getMatrices().pop();
    }

    public static void renderImage(DrawContext context, GuiElement element) {
        if (element.texture == null) return;
        context.drawTexture(element.texture, element.x, element.y, 0, 0, element.width, element.height, element.texWidth, element.texHeight);
    }
}