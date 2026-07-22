package com.crissyjuanxd.viciontguis.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

        try {
            JsonObject guiData = JsonParser.parseString(jsonPayload).getAsJsonObject();
            GuiElementFactory.ParseResult result = GuiElementFactory.parse(guiData, this.textRenderer);
            this.background = result.background();
            this.interactableElements.addAll(result.elements());
        } catch (Exception e) {
            System.err.println("Error parseando el JSON: " + e.getMessage());
        }
    }

    // NUEVO MÉTODO: Actualiza los elementos sin reiniciar la animación
    public void updateData(String newJsonPayload) {
        try {
            JsonObject guiData = JsonParser.parseString(newJsonPayload).getAsJsonObject();
            GuiElementFactory.ParseResult result = GuiElementFactory.parse(guiData, this.textRenderer);
            this.background = result.background();
            this.interactableElements.clear();
            this.interactableElements.addAll(result.elements());
        } catch (Exception e) {
            System.err.println("Error parseando el JSON en update: " + e.getMessage());
        }
    }

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

        if (isClosing && progress >= 1.0f) return;

        float finalScale = animScale * customScaleModifier;
        int screenWidth = this.width;
        int screenHeight = this.height;
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        int adjMouseX = (int) (centerX + (mouseX - centerX) / customScaleModifier);
        int adjMouseY = (int) (centerY + (mouseY - centerY) / customScaleModifier);

        GuiElement hoveredElement = null;
        int currentZ = 0;

        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(finalScale, finalScale, 1.0f);
        context.getMatrices().translate(-centerX, -centerY, 0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (background != null) {
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            int bgX = centerX - (background.width() / 2);
            int bgY = centerY - (background.height() / 2);
            context.drawTexture(background.texture(), bgX, bgY, 0, 0, background.width(), background.height(), background.texWidth(), background.texHeight());
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        for (GuiElement element : interactableElements) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            context.getMatrices().push();
            context.getMatrices().translate(0, 0, currentZ);

            boolean isHovered = animScale >= 1.0f && !isClosing && element.isHovered(adjMouseX, adjMouseY, screenWidth, screenHeight);

            if (isHovered && !element.type.equals("invisible_button")) {
                if (element.isButton || (element.tooltipLines != null && !element.tooltipLines.isEmpty())) {
                    hoveredElement = element;
                }
                if (element.isButton && !element.type.equals("item_slot") && !element.type.equals("entity")) {
                    context.setShaderColor(0.85F, 0.85F, 0.85F, 1.0F);
                } else {
                    context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                }
            } else {
                context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }

            switch (element.type) {
                case "item_slot" -> GuiElementRenderer.renderItemSlot(context, element, screenWidth, screenHeight, isHovered);
                case "entity" -> {
                    context.getMatrices().pop();
                    EntityRenderHandler.render(context, element, this.client, screenWidth, screenHeight, finalScale, mouseX, mouseY);
                    context.getMatrices().push();
                    context.getMatrices().translate(0, 0, currentZ);
                }
                case "text" -> GuiElementRenderer.renderText(context, this.textRenderer, element, screenWidth, screenHeight);
                case "rich_text" -> GuiElementRenderer.renderRichText(context, this.textRenderer, element, screenWidth, screenHeight);
                case "invisible_button" -> {}
                default -> GuiElementRenderer.renderImage(context, element, screenWidth, screenHeight);
            }

            context.getMatrices().pop();
            currentZ += 10;
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        RenderSystem.disableBlend();
        context.getMatrices().pop();

        if (hoveredElement != null && !hoveredElement.tooltipLines.isEmpty()) {
            List<net.minecraft.text.OrderedText> wrappedTooltip = new ArrayList<>();
            for (net.minecraft.text.Text line : hoveredElement.tooltipLines) {
                if (line.getString().isEmpty()) {
                    wrappedTooltip.add(net.minecraft.text.OrderedText.EMPTY);
                } else {
                    wrappedTooltip.addAll(this.textRenderer.wrapLines(line, 1000));
                }
            }
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 1000);
            context.drawOrderedTooltip(this.textRenderer, wrappedTooltip, mouseX, mouseY);
            context.getMatrices().pop();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isClosing || (Util.getMeasuringTimeMs() - animationStartTime < ANIM_DURATION_MS)) {
            return false;
        }

        if (button == 0) {
            int screenWidth = this.width;
            int screenHeight = this.height;
            int centerX = screenWidth / 2;
            int centerY = screenHeight / 2;

            int adjMouseX = (int) (centerX + (mouseX - centerX) / customScaleModifier);
            int adjMouseY = (int) (centerY + (mouseY - centerY) / customScaleModifier);

            for (int i = interactableElements.size() - 1; i >= 0; i--) {
                GuiElement element = interactableElements.get(i);
                if (element.isButton && element.isHovered(adjMouseX, adjMouseY, screenWidth, screenHeight)) {
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