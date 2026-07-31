package com.crissyjuanxd.viciontguis.client.gui;

import com.crissyjuanxd.viciontguis.client.ViciontGuisClient;
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
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.lang.reflect.Field;
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

    private static long lastHoverSoundTime = 0;

    private static boolean checkedCinematic = false;
    private static Field cinematicPlayingField = null;

    public static void init() {
        HudRenderCallback.EVENT.register(OverlayManager::renderHud);
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen) {
                ScreenEvents.afterRender(screen).register(OverlayManager::renderInventory);
                ScreenMouseEvents.beforeMouseClick(screen).register(OverlayManager::handleInventoryClick);
            }
        });
    }

    private static float getFixedScaleModifier() {
        MinecraftClient client = MinecraftClient.getInstance();
        float guiScale = (float) client.getWindow().getScaleFactor();
        int screenHeightPx = client.getWindow().getFramebufferHeight();
        float resolutionScale = screenHeightPx / 1080.0f;
        return (1.0f / guiScale) * (resolutionScale * 3.2f);
    }

    public static void addOverlay(String id, String target, GuiElementFactory.ParseResult parsed) {
        if ("hud".equals(target)) {
            hudOverlays.put(id, parsed);
        } else if ("inventory".equals(target)) {
            GuiElementFactory.ParseResult old = invOverlays.get(id);
            if (old != null) {
                for (GuiElement newEl : parsed.elements()) {
                    for (GuiElement oldEl : old.elements()) {
                        if (newEl.id.equals(oldEl.id) && oldEl.wasHovered) {
                            newEl.wasHovered = true;
                            break;
                        }
                    }
                }
            }
            invOverlays.put(id, parsed);
        }
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

    private static void playSound(String soundId, float pitch, float volume) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (soundId != null && !soundId.isEmpty()) {
            float finalVolume = volume * ViciontGuisClient.MENU_VOLUME;
            client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvent.of(Identifier.of(soundId)), pitch, finalVolume));
        }
    }

    private static boolean isCinematicPlaying() {
        if (!checkedCinematic) {
            try {
                Class<?> clazz = Class.forName("com.vctcinematics.core.CinematicManager");
                cinematicPlayingField = clazz.getField("isPlaying");
            } catch (Exception e) {
            }
            checkedCinematic = true;
        }

        if (cinematicPlayingField != null) {
            try {
                return cinematicPlayingField.getBoolean(null);
            } catch (Exception e) { }
        }
        return false;
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (MinecraftClient.getInstance().options.hudHidden || hudOverlays.isEmpty() || isCinematicPlaying()) return;

        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();

        for (Map.Entry<String, GuiElementFactory.ParseResult> entry : hudOverlays.entrySet()) {
            GuiElementFactory.ParseResult overlay = entry.getValue();

            context.getMatrices().push();
            int virtualSw = sw;
            int virtualSh = sh;
            float scaleMod = 1.0f;

            if (overlay.fixedScale()) {
                scaleMod = getFixedScaleModifier();
                virtualSw = (int) (sw / scaleMod);
                virtualSh = (int) (sh / scaleMod);

                context.getMatrices().translate(sw / 2f, sh / 2f, 0);
                context.getMatrices().scale(scaleMod, scaleMod, 1.0f);
                context.getMatrices().translate(-virtualSw / 2f, -virtualSh / 2f, 0);
            }

            renderOverlayBackground(context, overlay, 0, 0, virtualSw, virtualSh);
            for (GuiElement element : overlay.elements()) {
                processAnimations(entry.getKey(), element);
                renderElement(context, element, 0, 0, false, false, sw, sh, virtualSw, virtualSh, scaleMod);
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
            float scaleMod = 1.0f;
            int virtualSw = screen.width;
            int virtualSh = screen.height;

            if (overlay.fixedScale()) {
                scaleMod = getFixedScaleModifier();
                virtualSw = (int) (screen.width / scaleMod);
                virtualSh = (int) (screen.height / scaleMod);

                context.getMatrices().translate(screen.width / 2f, screen.height / 2f, 0);
                context.getMatrices().scale(scaleMod, scaleMod, 1.0f);
                context.getMatrices().translate(-virtualSw / 2f, -virtualSh / 2f, 0);
            }

            int adjMouseX = overlay.fixedScale() ? (int) (virtualSw / 2f + (mouseX - screen.width / 2f) / scaleMod) : mouseX;
            int adjMouseY = overlay.fixedScale() ? (int) (virtualSh / 2f + (mouseY - screen.height / 2f) / scaleMod) : mouseY;

            renderOverlayBackground(context, overlay, shiftX, shiftY, virtualSw, virtualSh);
            for (GuiElement element : overlay.elements()) {
                processAnimations(entry.getKey(), element);

                boolean isHovered = element.isHovered(adjMouseX, adjMouseY, virtualSw, virtualSh, shiftX, shiftY);
                if (isHovered && element.isButton) hoveredElement = element;

                if (isHovered && !element.type.equals("invisible_button")) {
                    if (!element.wasHovered) {
                        if (element.hoverSound != null && !element.hoverSound.isEmpty()) {
                            long now = Util.getMeasuringTimeMs();
                            if (now - lastHoverSoundTime > 60) {
                                playSound(element.hoverSound, element.hoverPitch, element.hoverVolume);
                                lastHoverSoundTime = now;
                            }
                        }
                        element.wasHovered = true;
                    }
                } else {
                    element.wasHovered = false;
                }

                renderElement(context, element, shiftX, shiftY, isHovered, true, screen.width, screen.height, virtualSw, virtualSh, scaleMod);
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

    private static void renderOverlayBackground(DrawContext context, GuiElementFactory.ParseResult overlay, int shiftX, int shiftY, int virtualSw, int virtualSh) {
        if (overlay.background() == null) return;
        int bgX = (virtualSw / 2) + shiftX - (overlay.background().width() / 2);
        int bgY = (virtualSh / 2) + shiftY - (overlay.background().height() / 2);
        context.drawTexture(overlay.background().texture(), bgX, bgY, 0, 0, overlay.background().width(), overlay.background().height(), overlay.background().texWidth(), overlay.background().texHeight());
    }

    private static void renderElement(DrawContext context, GuiElement element, int shiftX, int shiftY, boolean isHovered, boolean isInventory, int physSw, int physSh, int virtualSw, int virtualSh, float scaleMod) {
        MinecraftClient client = MinecraftClient.getInstance();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (isHovered && isInventory && element.isButton && !element.type.equals("item_slot") && !element.type.equals("entity")) {
            context.fill(element.getRenderX(virtualSw, shiftX), element.getRenderY(virtualSh, shiftY),
                    element.getRenderX(virtualSw, shiftX) + element.width, element.getRenderY(virtualSh, shiftY) + element.height, 0x40000000);
        }

        switch (element.type) {
            case "item_slot" -> GuiElementRenderer.renderItemSlot(context, element, virtualSw, virtualSh, isHovered, shiftX, shiftY);
            case "entity" -> {
                // Dibuja la textura en el espacio virtual activo
                if (element.texture != null) {
                    context.drawTexture(element.texture, element.getRenderX(virtualSw, shiftX), element.getRenderY(virtualSh, shiftY), 0, 0, element.width, element.height, element.texWidth, element.texHeight);
                }

                // Sale temporalmente de la escala virtual para coordenadas absolutas
                context.getMatrices().pop();

                EntityRenderHandler.render(context, element, client, physSw, physSh, virtualSw, virtualSh, scaleMod, -1, -1, shiftX, shiftY);

                // Vuelve a aplicar la escala virtual
                context.getMatrices().push();
                if (scaleMod != 1.0f) {
                    context.getMatrices().translate(physSw / 2f, physSh / 2f, 0);
                    context.getMatrices().scale(scaleMod, scaleMod, 1.0f);
                    context.getMatrices().translate(-virtualSw / 2f, -virtualSh / 2f, 0);
                }
            }
            case "text" -> GuiElementRenderer.renderText(context, client.textRenderer, element, virtualSw, virtualSh, shiftX, shiftY);
            case "rich_text" -> GuiElementRenderer.renderRichText(context, client.textRenderer, element, virtualSw, virtualSh, shiftX, shiftY);
            case "invisible_button" -> {}
            default -> GuiElementRenderer.renderImage(context, element, virtualSw, virtualSh, shiftX, shiftY);
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
            int virtualSw = overlay.fixedScale() ? (int) (screen.width / scaleMod) : screen.width;
            int virtualSh = overlay.fixedScale() ? (int) (screen.height / scaleMod) : screen.height;

            int adjMouseX = overlay.fixedScale() ? (int) (virtualSw / 2f + (mouseX - screen.width / 2f) / scaleMod) : (int) mouseX;
            int adjMouseY = overlay.fixedScale() ? (int) (virtualSh / 2f + (mouseY - screen.height / 2f) / scaleMod) : (int) mouseY;

            for (GuiElement element : overlay.elements()) {
                if (element.isButton && element.isHovered(adjMouseX, adjMouseY, virtualSw, virtualSh, shiftX, shiftY)) {

                    if (element.clickSound != null && !element.clickSound.isEmpty()) {
                        playSound(element.clickSound, element.clickPitch, element.clickVolume);
                    }

                    if (element.action != null) GuiNetworkHandler.sendAction(entry.getKey(), element.action);
                    return false;
                }
            }
        }
        return true;
    }
}