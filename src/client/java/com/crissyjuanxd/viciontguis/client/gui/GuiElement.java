package com.crissyjuanxd.viciontguis.client.gui;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public class GuiElement {

    public final String id;
    public final String type;
    public final String action;
    public final String anchor;

    public final Identifier texture;
    public final ItemStack mcItem;

    public final int offsetX, offsetY, width, height;
    public final int texWidth, texHeight;

    public final boolean isButton;
    public final List<Text> tooltipLines;

    public final String text;
    public final int textColor;
    public final float textScale;
    public final boolean textBold;

    public final String entityId;
    public final String entityName;
    public final int entityScale;

    public final List<OrderedText> richLines;
    public final int richColor;
    public final float richScale;

    public final boolean richOutline;

    LivingEntity cachedEntity = null;
    boolean entityInitAttempted = false;

    public GuiElement(String id, String type, Identifier texture, ItemStack mcItem, String anchor, int offsetX, int offsetY, int width, int height,
                      int texWidth, int texHeight, boolean isButton, List<Text> tooltipLines, String action,
                      String text, int textColor, float textScale, boolean textBold,
                      String entityId, String entityName, int entityScale,
                      List<OrderedText> richLines, int richColor, float richScale, boolean richOutline) {
        this.id = id;
        this.type = type;
        this.texture = texture;
        this.mcItem = mcItem;
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.width = width;
        this.height = height;
        this.texWidth = texWidth;
        this.texHeight = texHeight;
        this.isButton = isButton;
        this.tooltipLines = tooltipLines;
        this.action = action;
        this.text = text;
        this.textColor = textColor;
        this.textScale = textScale;
        this.textBold = textBold;
        this.entityId = entityId;
        this.entityName = entityName;
        this.entityScale = entityScale;
        this.richLines = richLines;
        this.richColor = richColor;
        this.richScale = richScale;
        this.richOutline = richOutline;
    }

    public int getBaseX(int screenWidth) {
        if (anchor == null) return screenWidth / 2;
        return switch (anchor) {
            case "top_left", "bottom_left", "left" -> 0;
            case "top_right", "bottom_right", "right" -> screenWidth;
            default -> screenWidth / 2;
        };
    }

    public int getBaseY(int screenHeight) {
        if (anchor == null) return screenHeight / 2;
        return switch (anchor) {
            case "top_left", "top_right", "top_center" -> 0;
            case "bottom_left", "bottom_right", "bottom_center" -> screenHeight;
            default -> screenHeight / 2;
        };
    }

    // OPTIMIZACIÓN + fix del shift: un único lugar que calcula rx/ry,
    // en vez de que cada método de render lo recalculara por su cuenta.
    public int getRenderX(int screenWidth, int shiftX) {
        return getBaseX(screenWidth) + shiftX + offsetX - (width / 2);
    }

    public int getRenderY(int screenHeight, int shiftY) {
        return getBaseY(screenHeight) + shiftY + offsetY - (height / 2);
    }

    public boolean isHovered(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        return isHovered(mouseX, mouseY, screenWidth, screenHeight, 0, 0);
    }

    // Nueva variante: permite desplazar el hitbox junto con el panel
    // (usada cuando el libro de recetas mueve el InventoryScreen).
    public boolean isHovered(int mouseX, int mouseY, int screenWidth, int screenHeight, int shiftX, int shiftY) {
        int rx = getRenderX(screenWidth, shiftX);
        int ry = getRenderY(screenHeight, shiftY);
        return mouseX >= rx && mouseX <= rx + width && mouseY >= ry && mouseY <= ry + height;
    }
}