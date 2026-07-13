package com.crissyjuanxd.viciontguis.client.gui;

import net.minecraft.util.Identifier;

/**
 * Textura de fondo de una GUI dinámica, con su tamaño lógico y el tamaño real
 * del archivo de textura (para el UV scaling de drawTexture).
 */
public record GuiBackground(Identifier texture, int width, int height, int texWidth, int texHeight) {
}