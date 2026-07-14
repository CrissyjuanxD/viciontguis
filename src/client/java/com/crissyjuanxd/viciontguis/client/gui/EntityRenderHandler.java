package com.crissyjuanxd.viciontguis.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class EntityRenderHandler {

    private EntityRenderHandler() {}

    public static LivingEntity getOrCreateEntity(GuiElement element, MinecraftClient client) {
        if (element.entityInitAttempted) return element.cachedEntity;
        element.entityInitAttempted = true;

        if (element.entityId == null || client.world == null) return null;

        try {
            EntityType<?> entityType = Registries.ENTITY_TYPE.get(Identifier.of(element.entityId));
            Entity entity = entityType.create(client.world);
            if (entity instanceof LivingEntity le) {
                element.cachedEntity = le;
                le.setUuid(UUID.randomUUID());
                if (client.player != null) {
                    le.setPosition(client.player.getX(), client.player.getY(), client.player.getZ());
                } else {
                    le.setPosition(0, client.world.getSeaLevel(), 0);
                }

                if (element.entityName != null && !element.entityName.isEmpty()) {
                    le.setCustomName(Text.literal(element.entityName));
                    le.setCustomNameVisible(false);
                }
            }
        } catch (Exception e) {
            System.err.println("Error al instanciar la entidad para la GUI: " + element.entityId);
        }
        return element.cachedEntity;
    }

    public static void render(DrawContext context, GuiElement element, MinecraftClient client,
                              int screenWidth, int screenHeight, float finalScale, int mouseX, int mouseY) {
        render(context, element, client, screenWidth, screenHeight, finalScale, mouseX, mouseY, 0, 0);
    }

    public static void render(DrawContext context, GuiElement element, MinecraftClient client,
                              int screenWidth, int screenHeight, float finalScale, int mouseX, int mouseY,
                              int shiftX, int shiftY) {

        int rx = element.getRenderX(screenWidth, shiftX);
        int ry = element.getRenderY(screenHeight, shiftY);

        if (element.texture != null) {
            context.drawTexture(element.texture, rx, ry, 0, 0, element.width, element.height, element.texWidth, element.texHeight);
        }

        LivingEntity entity = getOrCreateEntity(element, client);
        if (entity == null) return;

        if (client.world != null) {
            entity.age = (int) client.world.getTime();
        }

        context.getMatrices().pop();

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        int absX1 = (int) (centerX + (rx + 2 - centerX) * finalScale);
        int absY1 = (int) (centerY + (ry + 2 - centerY) * finalScale);
        int absX2 = (int) (centerX + (rx + element.width - 2 - centerX) * finalScale);
        int absY2 = (int) (centerY + (ry + element.height - 4 - centerY) * finalScale);
        int scaledSize = (int) (element.entityScale * finalScale);

        context.enableScissor(absX1, absY1, absX2, absY2);
        InventoryScreen.drawEntity(context, absX1, absY1, absX2, absY2, scaledSize, 0.0625f, mouseX, mouseY, entity);
        context.disableScissor();

        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(finalScale, finalScale, 1.0f);
        context.getMatrices().translate(-centerX, -centerY, 0);
    }
}