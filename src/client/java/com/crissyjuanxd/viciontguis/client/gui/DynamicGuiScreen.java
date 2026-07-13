package com.crissyjuanxd.viciontguis.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DynamicGuiScreen extends Screen {

    private final String jsonPayload;
    private final Consumer<String> actionHandler;
    private final List<GuiElement> interactableElements = new ArrayList<>();

    private GuiBackground background;

    private long animationStartTime;
    private static final long ANIM_DURATION_MS = 300L;
    private boolean isClosing = false;

    private float customScaleModifier = 1.0f;
    private static final float BASE_MENU_SCALE = 3.2f;

    public DynamicGuiScreen(String jsonPayload, Consumer<String> actionHandler) {
        super(Text.literal("Viciont Custom GUI"));
        this.jsonPayload = jsonPayload;
        this.actionHandler = actionHandler;
    }

    @Override
    protected void init() {
        super.init();
        this.interactableElements.clear();
        this.animationStartTime = Util.getMeasuringTimeMs();

        if (this.client != null) {
            float guiScale = (float) this.client.getWindow().getScaleFactor();
            int screenHeightPx = this.client.getWindow().getFramebufferHeight();
            float resolutionScale = screenHeightPx / 1080.0f;
            this.customScaleModifier = (1.0f / guiScale) * (resolutionScale * BASE_MENU_SCALE);
        }

        Gson gson = new Gson();
        try {
            JsonObject guiData = gson.fromJson(jsonPayload, JsonObject.class);
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            GuiElementFactory.ParseResult result = GuiElementFactory.parse(guiData, centerX, centerY, this.textRenderer);
            this.background = result.background();
            this.interactableElements.addAll(result.elements());
        } catch (Exception e) {
            System.err.println("Error parseando el JSON: " + e.getMessage());
        }
    }

    /**
     * FIX real del bug del flash de FancyMenu al cerrar.
     * Antes el cierre se completaba dentro de render() (mid-frame, vía client.execute()),
     * lo que igual podía dejar que otros mods que escuchan la screen (FancyMenu incluido)
     * vieran un estado "screen == null" en un momento raro del frame.
     * tick() corre en un punto fijo del game loop, nunca a mitad de un render, así que
     * cerrar acá es el punto correcto y predecible del ciclo del juego.
     */
    @Override
    public void tick() {
        if (isClosing && Util.getMeasuringTimeMs() - animationStartTime >= ANIM_DURATION_MS) {
            super.close();
        }
    }

    @Override
    public void close() {
        if (!isClosing) {
            this.isClosing = true;
            this.animationStartTime = Util.getMeasuringTimeMs();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x99000000);

        long elapsedTime = Util.getMeasuringTimeMs() - animationStartTime;
        float progress = Math.min((float) elapsedTime / ANIM_DURATION_MS, 1.0f);

        float animScale = isClosing
                ? 1.0f - (progress * progress * progress)
                : 1.0f - (float) Math.pow(1.0f - progress, 3);

        if (isClosing && progress >= 1.0f) {
            // No queda nada que valga la pena dibujar; tick() va a terminar el cierre
            // en cualquier momento. El overlay oscuro ya se dibujó arriba.
            return;
        }

        float finalScale = animScale * customScaleModifier;
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int adjMouseX = (int) (centerX + (mouseX - centerX) / customScaleModifier);
        int adjMouseY = (int) (centerY + (mouseY - centerY) / customScaleModifier);

        GuiElement hoveredElement = null;

        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(finalScale, finalScale, 1.0f);
        context.getMatrices().translate(-centerX, -centerY, 0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (background != null) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            int bgX = centerX - (background.width() / 2);
            int bgY = centerY - (background.height() / 2);
            context.drawTexture(background.texture(), bgX, bgY, 0, 0, background.width(), background.height(), background.texWidth(), background.texHeight());
        }

        for (GuiElement element : interactableElements) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            boolean isHovered = animScale >= 1.0f && !isClosing && element.isHovered(adjMouseX, adjMouseY);
            if (isHovered && !element.type.equals("invisible_button")) {
                hoveredElement = element;
                if (element.isButton && !element.type.equals("item_slot") && !element.type.equals("entity")) {
                    RenderSystem.setShaderColor(0.85F, 0.85F, 0.85F, 1.0F);
                }
            }

            switch (element.type) {
                case "item_slot" -> GuiElementRenderer.renderItemSlot(context, element, isHovered);
                case "entity" -> EntityRenderHandler.render(context, element, this.client, centerX, centerY, finalScale, mouseX, mouseY);
                case "text" -> GuiElementRenderer.renderText(context, this.textRenderer, element);
                case "rich_text" -> GuiElementRenderer.renderRichText(context, this.textRenderer, element);
                case "invisible_button" -> { /* no dibuja nada */ }
                default -> GuiElementRenderer.renderImage(context, element);
            }
        }

        RenderSystem.disableBlend();
        context.getMatrices().pop();

        if (hoveredElement != null && !hoveredElement.tooltipLines.isEmpty()) {
            context.drawTooltip(this.textRenderer, hoveredElement.tooltipLines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isClosing || (Util.getMeasuringTimeMs() - animationStartTime < ANIM_DURATION_MS)) {
            return false;
        }

        if (button == 0) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int adjMouseX = (int) (centerX + (mouseX - centerX) / customScaleModifier);
            int adjMouseY = (int) (centerY + (mouseY - centerY) / customScaleModifier);

            for (int i = interactableElements.size() - 1; i >= 0; i--) {
                GuiElement element = interactableElements.get(i);
                if (element.isButton && element.isHovered(adjMouseX, adjMouseY)) {
                    if (element.action != null && actionHandler != null) {
                        actionHandler.accept(element.action);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}