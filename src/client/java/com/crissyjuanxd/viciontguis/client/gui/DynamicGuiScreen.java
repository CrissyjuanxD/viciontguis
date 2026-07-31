package com.crissyjuanxd.viciontguis.client.gui;

import com.crissyjuanxd.viciontguis.client.ViciontGuisClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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

    private String openSound;
    private float openPitch;
    private float openVolume;
    private String closeSound;
    private float closePitch;
    private float closeVolume;
    private boolean openSoundPlayed = false;

    private static long lastHoverSoundTime = 0;

    public DynamicGuiScreen(String jsonPayload, Consumer<String> actionHandler) {
        super(Text.literal("Viciont Custom GUI"));
        this.jsonPayload = jsonPayload;
        this.actionHandler = actionHandler;
    }

    private void playSound(String soundId, float pitch, float volume) {
        if (this.client != null && soundId != null && !soundId.isEmpty()) {
            float finalVolume = volume * ViciontGuisClient.MENU_VOLUME;
            this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvent.of(Identifier.of(soundId)), pitch, finalVolume));
        }
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

            this.openSound = result.openSound();
            this.openPitch = result.openPitch();
            this.openVolume = result.openVolume();
            this.closeSound = result.closeSound();
            this.closePitch = result.closePitch();
            this.closeVolume = result.closeVolume();

            if (!openSoundPlayed && this.openSound != null && !this.openSound.isEmpty()) {
                playSound(this.openSound, this.openPitch, this.openVolume);
                openSoundPlayed = true;
            }
        } catch (Exception e) {
            System.err.println("Error parseando el JSON: " + e.getMessage());
        }
    }

    public void updateData(String newJsonPayload) {
        try {
            JsonObject guiData = JsonParser.parseString(newJsonPayload).getAsJsonObject();
            GuiElementFactory.ParseResult result = GuiElementFactory.parse(guiData, this.textRenderer);
            this.background = result.background();

            List<String> hoveredIds = new ArrayList<>();
            for (GuiElement el : this.interactableElements) {
                if (el.wasHovered) hoveredIds.add(el.id);
            }

            this.interactableElements.clear();
            this.interactableElements.addAll(result.elements());

            for (GuiElement el : this.interactableElements) {
                if (hoveredIds.contains(el.id)) el.wasHovered = true;
            }
        } catch (Exception e) {
            System.err.println("Error parseando el JSON en update: " + e.getMessage());
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ViciontGuisClient.GuiKey.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
            if (this.closeSound != null && !this.closeSound.isEmpty()) {
                playSound(this.closeSound, this.closePitch, this.closeVolume);
            }
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

        // RESOLUCIÓN VIRTUAL ESTABLE: Se calcula solo con el modificador, eliminando el tambaleo
        int virtualSw = (int) (this.width / customScaleModifier);
        int virtualSh = (int) (this.height / customScaleModifier);

        int adjMouseX = (int) (virtualSw / 2f + (mouseX - this.width / 2f) / finalScale);
        int adjMouseY = (int) (virtualSh / 2f + (mouseY - this.height / 2f) / finalScale);

        GuiElement hoveredElement = null;
        int currentZ = 0;

        context.getMatrices().push();
        // Escalar desde el centro real para mantener las proporciones exactas
        context.getMatrices().translate(this.width / 2f, this.height / 2f, 0);
        context.getMatrices().scale(finalScale, finalScale, 1.0f);
        context.getMatrices().translate(-virtualSw / 2f, -virtualSh / 2f, 0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (background != null) {
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            int bgX = (virtualSw / 2) - (background.width() / 2);
            int bgY = (virtualSh / 2) - (background.height() / 2);
            context.drawTexture(background.texture(), bgX, bgY, 0, 0, background.width(), background.height(), background.texWidth(), background.texHeight());
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        for (GuiElement element : interactableElements) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            context.getMatrices().push();
            context.getMatrices().translate(0, 0, currentZ);

            boolean isHovered = animScale >= 1.0f && !isClosing && element.isHovered(adjMouseX, adjMouseY, virtualSw, virtualSh);

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

                if (element.isButton || (element.tooltipLines != null && !element.tooltipLines.isEmpty())) {
                    hoveredElement = element;
                }
                if (element.isButton && !element.type.equals("item_slot") && !element.type.equals("entity")) {
                    context.setShaderColor(0.85F, 0.85F, 0.85F, 1.0F);
                } else {
                    context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                }
            } else {
                element.wasHovered = false;
                context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }

            switch (element.type) {
                case "item_slot" -> GuiElementRenderer.renderItemSlot(context, element, virtualSw, virtualSh, isHovered);
                case "entity" -> {
                    // Dibuja el marco de la entidad en espacio virtual
                    if (element.texture != null) {
                        int rx = element.getRenderX(virtualSw, 0);
                        int ry = element.getRenderY(virtualSh, 0);
                        context.drawTexture(element.texture, rx, ry, 0, 0, element.width, element.height, element.texWidth, element.texHeight);
                    }

                    // Extrae la matriz virtual para usar renderizado físico (Absoluto)
                    context.getMatrices().pop(); // Quita transformacion de elemento
                    context.getMatrices().pop(); // Quita transformacion principal

                    EntityRenderHandler.render(context, element, this.client, this.width, this.height, virtualSw, virtualSh, finalScale, mouseX, mouseY, 0, 0);

                    // Restaura las matrices
                    context.getMatrices().push();
                    context.getMatrices().translate(this.width / 2f, this.height / 2f, 0);
                    context.getMatrices().scale(finalScale, finalScale, 1.0f);
                    context.getMatrices().translate(-virtualSw / 2f, -virtualSh / 2f, 0);
                    context.getMatrices().push();
                    context.getMatrices().translate(0, 0, currentZ);
                }
                case "text" -> GuiElementRenderer.renderText(context, this.textRenderer, element, virtualSw, virtualSh);
                case "rich_text" -> GuiElementRenderer.renderRichText(context, this.textRenderer, element, virtualSw, virtualSh);
                case "invisible_button" -> {}
                default -> GuiElementRenderer.renderImage(context, element, virtualSw, virtualSh);
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
            float finalScale = customScaleModifier;
            int virtualSw = (int) (this.width / finalScale);
            int virtualSh = (int) (this.height / finalScale);

            int adjMouseX = (int) (virtualSw / 2f + (mouseX - this.width / 2f) / finalScale);
            int adjMouseY = (int) (virtualSh / 2f + (mouseY - this.height / 2f) / finalScale);

            for (int i = interactableElements.size() - 1; i >= 0; i--) {
                GuiElement element = interactableElements.get(i);
                if (element.isButton && element.isHovered(adjMouseX, adjMouseY, virtualSw, virtualSh)) {

                    if (element.clickSound != null && !element.clickSound.isEmpty()) {
                        playSound(element.clickSound, element.clickPitch, element.clickVolume);
                    }

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