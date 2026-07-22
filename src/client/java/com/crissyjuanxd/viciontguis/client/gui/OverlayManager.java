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
import net.minecraft.text.OrderedText;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OverlayManager {
    private static final Map<String, GuiElementFactory.ParseResult> hudOverlays = new ConcurrentHashMap<>();
    private static final Map<String, GuiElementFactory.ParseResult> invOverlays = new ConcurrentHashMap<>();

    private static final Map<String, AnimState> animStates = new ConcurrentHashMap<>();
    private static class AnimState {
        float x, y;
        long lastTime;
    }

    public static void init() {
        HudRenderCallback.EVENT.register(OverlayManager::renderHud);
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen) {
                ScreenEvents.afterRender(screen).register(OverlayManager::renderInventory);
                ScreenMouseEvents.beforeMouseClick(screen).register(OverlayManager::handleInventoryClick);
            }
        });
    }

    // NUEVO: Fórmula base de escala fija para evitar reescribirla
    private static float getFixedScaleModifier() {
        MinecraftClient client = MinecraftClient.getInstance();
        float guiScale = (float) client.getWindow().getScaleFactor();
        int screenHeightPx = client.getWindow().getFramebufferHeight();
        float resolutionScale = screenHeightPx / 1080.0f;
        return (1.0f / guiScale) * (resolutionScale * 3.2f); // 3.2f = BASE_MENU_SCALE
    }

    public static void addOverlay(String id, String target, GuiElementFactory.ParseResult parsed) {
        if ("hud".equals(target)) hudOverlays.put(id, parsed);
        else if ("inventory".equals(target)) invOverlays.put(id, parsed);
    }

    public static boolean removeOverlay(String id) {
        animStates.keySet().removeIf(k -> k.startsWith(id + "_"));
        return hudOverlays.remove(id) != null || invOverlays.remove(id) != null;
    }

    private static void processAnimations(String overlayId, GuiElement element) {
        long now = Util.getMeasuringTimeMs();
        String key = overlayId + "_" + element.id;

        AnimState state = animStates.computeIfAbsent(key, k -> {
            AnimState s = new AnimState();
            s.x = element.targetOffsetX;
            s.y = element.targetOffsetY;
            s.lastTime = now;
            return s;
        });

        float dt = (now - state.lastTime) / 1000f;
        state.lastTime = now;

        if (element.animSpeed > 0) {
            state.x += (element.targetOffsetX - state.x) * element.animSpeed * dt;
            state.y += (element.targetOffsetY - state.y) * element.animSpeed * dt;

            if (Math.abs(element.targetOffsetX - state.x) < 0.5f) state.x = element.targetOffsetX;
            if (Math.abs(element.targetOffsetY - state.y) < 0.5f) state.y = element.targetOffsetY;
        } else {
            state.x = element.targetOffsetX;
            state.y = element.targetOffsetY;
        }

        element.offsetX = (int) state.x;
        element.offsetY = (int) state.y;
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (MinecraftClient.getInstance().options.hudHidden || hudOverlays.isEmpty()) return;

        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();

        for (Map.Entry<String, GuiElementFactory.ParseResult> entry : hudOverlays.entrySet()) {
            GuiElementFactory.ParseResult overlay = entry.getValue();

            context.getMatrices().push();

            // APLICAR ESCALA FIJA AL HUD SI ESTÁ ACTIVADA
            if (overlay.fixedScale()) {
                float scaleMod = getFixedScaleModifier();
                context.getMatrices().translate(sw / 2f, sh / 2f, 0);
                context.getMatrices().scale(scaleMod, scaleMod, 1.0f);
                context.getMatrices().translate(-sw / 2f, -sh / 2f, 0);
            }

            renderOverlayBackground(context, overlay, 0, 0, sw, sh);
            for (GuiElement element : overlay.elements()) {
                processAnimations(entry.getKey(), element);
                renderElement(context, element, 0, 0, false, false, sw, sh);
            }
            context.getMatrices().pop();
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
        for (Map.Entry<String, GuiElementFactory.ParseResult> entry : invOverlays.entrySet()) {
            GuiElementFactory.ParseResult overlay = entry.getValue();

            context.getMatrices().push();
            float scaleMod = overlay.fixedScale() ? getFixedScaleModifier() : 1.0f;

            // APLICAR ESCALA FIJA AL INVENTARIO SI ESTÁ ACTIVADA
            if (overlay.fixedScale()) {
                context.getMatrices().translate(screen.width / 2f, screen.height / 2f, 0);
                context.getMatrices().scale(scaleMod, scaleMod, 1.0f);
                context.getMatrices().translate(-screen.width / 2f, -screen.height / 2f, 0);
            }

            // AJUSTE DEL RATÓN POR LA ESCALA
            int adjMouseX = overlay.fixedScale() ? (int) (screen.width / 2f + (mouseX - screen.width / 2f) / scaleMod) : mouseX;
            int adjMouseY = overlay.fixedScale() ? (int) (screen.height / 2f + (mouseY - screen.height / 2f) / scaleMod) : mouseY;

            renderOverlayBackground(context, overlay, shiftX, shiftY, screen.width, screen.height);
            for (GuiElement element : overlay.elements()) {
                processAnimations(entry.getKey(), element);

                boolean isHovered = element.isHovered(adjMouseX, adjMouseY, screen.width, screen.height, shiftX, shiftY);
                if (isHovered && element.isButton) hoveredElement = element;

                renderElement(context, element, shiftX, shiftY, isHovered, true, screen.width, screen.height);
            }
            context.getMatrices().pop();
        }

        if (hoveredElement != null && !hoveredElement.tooltipLines.isEmpty()) {
            List<net.minecraft.text.OrderedText> wrappedTooltip = new ArrayList<>();
            net.minecraft.client.font.TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            for (net.minecraft.text.Text line : hoveredElement.tooltipLines) {
                if (line.getString().isEmpty()) {
                    wrappedTooltip.add(net.minecraft.text.OrderedText.EMPTY);
                } else {
                    wrappedTooltip.addAll(textRenderer.wrapLines(line, 1000));
                }
            }
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 1000);
            context.drawOrderedTooltip(textRenderer, wrappedTooltip, mouseX, mouseY);
            context.getMatrices().pop();
        }
    }

    private static void renderOverlayBackground(DrawContext context, GuiElementFactory.ParseResult overlay, int shiftX, int shiftY, int sw, int sh) {
        if (overlay.background() == null) return;
        int bgX = (sw / 2) + shiftX - (overlay.background().width() / 2);
        int bgY = (sh / 2) + shiftY - (overlay.background().height() / 2);
        context.drawTexture(overlay.background().texture(), bgX, bgY, 0, 0, overlay.background().width(), overlay.background().height(), overlay.background().texWidth(), overlay.background().texHeight());
    }

    private static void renderElement(DrawContext context, GuiElement element, int shiftX, int shiftY, boolean isHovered, boolean isInventory, int sw, int sh) {
        MinecraftClient client = MinecraftClient.getInstance();

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
            GuiElementFactory.ParseResult overlay = entry.getValue();

            float scaleMod = overlay.fixedScale() ? getFixedScaleModifier() : 1.0f;
            int adjMouseX = overlay.fixedScale() ? (int) (screen.width / 2f + (mouseX - screen.width / 2f) / scaleMod) : (int) mouseX;
            int adjMouseY = overlay.fixedScale() ? (int) (screen.height / 2f + (mouseY - screen.height / 2f) / scaleMod) : (int) mouseY;

            for (GuiElement element : overlay.elements()) {
                if (element.isButton && element.isHovered(adjMouseX, adjMouseY, screen.width, screen.height, shiftX, shiftY)) {
                    if (element.action != null) GuiNetworkHandler.sendAction(entry.getKey(), element.action);
                    return false;
                }
            }
        }
        return true;
    }
}