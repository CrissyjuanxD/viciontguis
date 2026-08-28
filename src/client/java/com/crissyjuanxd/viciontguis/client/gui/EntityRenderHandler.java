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
            Entity entity = entityType.create(client.world, net.minecraft.entity.SpawnReason.COMMAND);
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
                              int physWidth, int physHeight, int virtWidth, int virtHeight, float scale, int mouseX, int mouseY,
                              int shiftX, int shiftY) {

        int rx = element.getRenderX(virtWidth, shiftX);
        int ry = element.getRenderY(virtHeight, shiftY);

        LivingEntity entity = getOrCreateEntity(element, client);
        if (entity == null) return;

        if (client.world != null) {
            entity.age = (int) client.world.getTime();
        }

        int physCenterX = physWidth / 2;
        int physCenterY = physHeight / 2;
        int virtCenterX = virtWidth / 2;
        int virtCenterY = virtHeight / 2;

        int absX1 = (int) (physCenterX + (rx + 2 - virtCenterX) * scale);
        int absY1 = (int) (physCenterY + (ry + 2 - virtCenterY) * scale);
        int absX2 = (int) (physCenterX + (rx + element.width - 2 - virtCenterX) * scale);
        int absY2 = (int) (physCenterY + (ry + element.height - 4 - virtCenterY) * scale);

        int scaledSize = (int) (element.entityScale * scale);

        // Ya no necesitamos empujar en Z, la 1.21.11 lo gestiona solo.
        context.enableScissor(absX1, absY1, absX2, absY2);
        InventoryScreen.drawEntity(context, absX1, absY1, absX2, absY2, scaledSize, mouseX, mouseY, 0.0625f, entity);
        context.disableScissor();
    }
}