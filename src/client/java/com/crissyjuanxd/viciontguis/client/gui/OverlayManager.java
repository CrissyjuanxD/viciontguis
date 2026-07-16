package com.crissyjuanxd.viciontguis.client.gui;

import com.crissyjuanxd.viciontguis.client.mixin.HandledScreenMixin;
import com.crissyjuanxd.viciontguis.client.network.GuiNetworkHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.RenderTickCounter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OverlayManager {
    private static final Map<String, GuiElementFactory.ParseResult> hudOverlays = new ConcurrentHashMap<>();
    private static final Map<String, GuiElementFactory.ParseResult> invOverlays = new ConcurrentHashMap<>();

    public static void init() {
        HudRenderCallback.EVENT.register(OverlayManager::renderHud);
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen) {
                ScreenEvents.afterRender(screen).register(OverlayManager::renderInventory);
                ScreenMouseEvents.beforeMouseClick(screen).register(OverlayManager::handleInventoryClick);
            }
        });
    }

    public static void addOverlay(String id, String target, GuiElementFactory.ParseResult parsed) {
        if ("hud".equals(target)) hudOverlays.put(id, parsed);
        else if ("inventory".equals(target)) invOverlays.put(id, parsed);
    }

    public static boolean removeOverlay(String id) {
        return hudOverlays.remove(id) != null || invOverlays.remove(id) != null;
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (MinecraftClient.getInstance().options.hudHidden || hudOverlays.isEmpty()) return;

        // HUD: Renderizado simple, sin detectar mouse ni tooltips
        for (GuiElementFactory.ParseResult overlay : hudOverlays.values()) {
            renderOverlayBackground(context, overlay, 0, 0);
            for (GuiElement element : overlay.elements()) {
                renderElement(context, element, 0, 0, false, false);
            }
        }
    }

    private static void renderInventory(Screen screen, DrawContext context, int mouseX, int mouseY, float tickDelta) {
        if (invOverlays.isEmpty()) return;

        int shiftX = 0, shiftY = 0;
        if (screen instanceof HandledScreen<?> handled) {
            HandledScreenMixin accessor = (HandledScreenMixin) handled;
            shiftX = (accessor.getX() + accessor.getBackgroundWidth() / 2) - (screen.width / 2);
            shiftY = (accessor.getY() + accessor.getBackgroundHeight() / 2) - (screen.height / 2);
        }

        GuiElement hoveredElement = null;
        for (GuiElementFactory.ParseResult overlay : invOverlays.values()) {
            renderOverlayBackground(context, overlay, shiftX, shiftY);
            for (GuiElement element : overlay.elements()) {
                boolean isHovered = element.isHovered(mouseX, mouseY, screen.width, screen.height, shiftX, shiftY);
                if (isHovered && element.isButton) hoveredElement = element;

                renderElement(context, element, shiftX, shiftY, isHovered, true);
            }
        }

        if (hoveredElement != null && !hoveredElement.tooltipLines.isEmpty()) {
            context.drawTooltip(MinecraftClient.getInstance().textRenderer, hoveredElement.tooltipLines, mouseX, mouseY);
        }
    }

    private static void renderOverlayBackground(DrawContext context, GuiElementFactory.ParseResult overlay, int shiftX, int shiftY) {
        if (overlay.background() == null) return;
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();
        int bgX = (sw / 2) + shiftX - (overlay.background().width() / 2);
        int bgY = (sh / 2) + shiftY - (overlay.background().height() / 2);
        context.drawTexture(overlay.background().texture(), bgX, bgY, 0, 0, overlay.background().width(), overlay.background().height(), overlay.background().texWidth(), overlay.background().texHeight());
    }

    private static void renderElement(DrawContext context, GuiElement element, int shiftX, int shiftY, boolean isHovered, boolean isInventory) {
        MinecraftClient client = MinecraftClient.getInstance();
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (isHovered && isInventory && element.isButton && !element.type.equals("item_slot") && !element.type.equals("entity")) {
            context.fill(element.getRenderX(sw, shiftX), element.getRenderY(sh, shiftY),
                    element.getRenderX(sw, shiftX) + element.width, element.getRenderY(sh, shiftY) + element.height, 0x40000000);
        }

        switch (element.type) {
            case "item_slot" -> GuiElementRenderer.renderItemSlot(context, element, sw, sh, isHovered, shiftX, shiftY);
            case "entity" -> EntityRenderHandler.render(context, element, client, sw, sh, 1.0f, -1, -1, shiftX, shiftY);
            case "text" -> GuiElementRenderer.renderText(context, client.textRenderer, element, sw, sh, shiftX, shiftY);
            case "rich_text" -> GuiElementRenderer.renderRichText(context, client.textRenderer, element, sw, sh, shiftX, shiftY);
            case "invisible_button" -> {}
            default -> GuiElementRenderer.renderImage(context, element, sw, sh, shiftX, shiftY);
        }

        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static boolean handleInventoryClick(Screen screen, double mouseX, double mouseY, int button) {
        if (button != 0 || invOverlays.isEmpty()) return true;
        int shiftX = 0, shiftY = 0;
        if (screen instanceof HandledScreen<?> handled) {
            HandledScreenMixin accessor = (HandledScreenMixin) handled;
            shiftX = (accessor.getX() + accessor.getBackgroundWidth() / 2) - (screen.width / 2);
            shiftY = (accessor.getY() + accessor.getBackgroundHeight() / 2) - (screen.height / 2);
        }

        for (Map.Entry<String, GuiElementFactory.ParseResult> entry : invOverlays.entrySet()) {
            for (GuiElement element : entry.getValue().elements()) {
                if (element.isButton && element.isHovered((int) mouseX, (int) mouseY, screen.width, screen.height, shiftX, shiftY)) {
                    if (element.action != null) GuiNetworkHandler.sendAction(entry.getKey(), element.action); // ahora con guiId
                    return false;
                }
            }
        }
        return true;
    }
}