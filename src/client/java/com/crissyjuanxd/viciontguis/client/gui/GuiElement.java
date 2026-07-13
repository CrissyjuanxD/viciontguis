package com.crissyjuanxd.viciontguis.client.gui;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Representa un elemento individual de una GUI dinámica (botón, texto, item, entidad, etc).
 * Es un simple contenedor de datos: el parseo lo hace {@link GuiElementFactory}
 * y el dibujado lo hace {@link GuiElementRenderer} / {@link EntityRenderHandler}.
 */
public class GuiElement {

    public final String id;
    public final String type;
    public final String action;

    public final Identifier texture;
    public final ItemStack mcItem;

    public final int x, y, width, height;
    public final int texWidth, texHeight;

    public final boolean isButton;
    public final List<Text> tooltipLines;

    // Texto simple
    public final String text;
    public final int textColor;
    public final float textScale;
    public final boolean textBold;

    // Entidad 3D
    public final String entityId;
    public final String entityName;
    public final int entityScale;

    // rich_text (tellraw-like)
    public final List<OrderedText> richLines;
    public final int richColor;
    public final float richScale;

    // Cache de la entidad instanciada (una sola vez por elemento/pantalla).
    // Vive acá porque el ciclo de vida de la entidad está atado al ciclo de vida
    // de este elemento, no al del renderer (que es stateless).
    LivingEntity cachedEntity = null;
    boolean entityInitAttempted = false;

    public GuiElement(String id, String type, Identifier texture, ItemStack mcItem, int x, int y, int width, int height,
                      int texWidth, int texHeight, boolean isButton, List<Text> tooltipLines, String action,
                      String text, int textColor, float textScale, boolean textBold,
                      String entityId, String entityName, int entityScale,
                      List<OrderedText> richLines, int richColor, float richScale) {
        this.id = id;
        this.type = type;
        this.texture = texture;
        this.mcItem = mcItem;
        this.x = x;
        this.y = y;
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
    }

    public boolean isHovered(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}