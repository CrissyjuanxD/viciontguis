package com.crissyjuanxd.viciontguis.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class DynamicGuiScreen extends Screen {

    private final String jsonPayload;
    private final Consumer<String> actionHandler;
    private JsonObject guiData;
    private final List<GuiElement> interactableElements = new ArrayList<>();

    private Identifier bgTexture = null;
    private int bgWidth = 0;
    private int bgHeight = 0;
    private int bgTexWidth = 0;
    private int bgTexHeight = 0;

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
            this.guiData = gson.fromJson(jsonPayload, JsonObject.class);
            parseGuiData();
        } catch (Exception e) {
            System.err.println("Error parseando el JSON: " + e.getMessage());
        }
    }

    private void parseGuiData() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (guiData.has("background")) {
            JsonObject bg = guiData.getAsJsonObject("background");
            this.bgTexture = Identifier.of(bg.get("texture").getAsString());
            this.bgWidth = bg.get("width").getAsInt();
            this.bgHeight = bg.get("height").getAsInt();
            this.bgTexWidth = bg.has("texture_width") ? bg.get("texture_width").getAsInt() : this.bgWidth;
            this.bgTexHeight = bg.has("texture_height") ? bg.get("texture_height").getAsInt() : this.bgHeight;
        }

        if (guiData.has("elements")) {
            JsonArray elements = guiData.getAsJsonArray("elements");
            for (JsonElement elem : elements) {
                JsonObject obj = elem.getAsJsonObject();
                String type = obj.get("type").getAsString();
                String id = obj.get("id").getAsString();

                Identifier texture = null;
                ItemStack mcItem = null;
                String entityId = null;
                String entityName = null;
                int entityScale = 30;

                if (type.equals("item_slot") || type.equals("custom_button") || type.equals("image") || type.equals("entity")) {
                    if (obj.has("texture")) {
                        texture = Identifier.of(obj.get("texture").getAsString());
                    }
                    if (type.equals("item_slot") && obj.has("item_id")) {
                        String itemIdStr = obj.get("item_id").getAsString();
                        Item item = Registries.ITEM.get(Identifier.of(itemIdStr));
                        mcItem = new ItemStack(item);

                        if (obj.has("custom_model_data")) {
                            int cmd = obj.get("custom_model_data").getAsInt();
                            mcItem.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(cmd));
                        }
                    }
                    if (type.equals("entity") && obj.has("entity_id")) {
                        entityId = obj.get("entity_id").getAsString();
                        if (obj.has("entity_scale")) {
                            entityScale = obj.get("entity_scale").getAsInt();
                        }
                        if (obj.has("entity_name")) {
                            entityName = obj.get("entity_name").getAsString();
                        }
                    }
                }

                int width = obj.has("width") ? obj.get("width").getAsInt() : 0;
                int height = obj.has("height") ? obj.get("height").getAsInt() : 0;
                int x = centerX + obj.get("x").getAsInt() - (width / 2);
                int y = centerY + obj.get("y").getAsInt() - (height / 2);

                int texWidth = obj.has("texture_width") ? obj.get("texture_width").getAsInt() : width;
                int texHeight = obj.has("texture_height") ? obj.get("texture_height").getAsInt() : height;

                boolean isButton = type.equals("custom_button") || type.equals("item_slot") || type.equals("invisible_button") || type.equals("entity");
                String action = obj.has("action") ? obj.get("action").getAsString() : null;

                List<Text> tooltipLines = new ArrayList<>();
                if (obj.has("tooltip")) {
                    JsonArray lines = obj.getAsJsonArray("tooltip");
                    for (JsonElement lineElem : lines) {
                        JsonObject lineObj = lineElem.getAsJsonObject();
                        String txt = lineObj.has("text") ? lineObj.get("text").getAsString() : "";
                        String color = lineObj.has("color") ? lineObj.get("color").getAsString() : "#FFFFFF";
                        boolean bold = lineObj.has("bold") && lineObj.get("bold").getAsBoolean();

                        MutableText mt = Text.literal(txt);
                        try {
                            mt.setStyle(mt.getStyle().withColor(TextColor.parse(color).getOrThrow()).withBold(bold));
                        } catch (Exception ignored) {}
                        tooltipLines.add(mt);
                    }
                }

                String textContent = null;
                int textColor = 0xFFFFFF;
                float textScale = 1.5f;
                boolean textBold = false;
                if (type.equals("text")) {
                    textContent = obj.has("text") ? obj.get("text").getAsString() : "";
                    if (obj.has("color")) {
                        try {
                            textColor = TextColor.parse(obj.get("color").getAsString()).getOrThrow().getRgb();
                        } catch (Exception ignored) {}
                    }
                    if (obj.has("scale")) {
                        textScale = obj.get("scale").getAsFloat();
                    }
                    textBold = obj.has("bold") && obj.get("bold").getAsBoolean();
                }

                // --- type = "rich_text" (mensajes estilo tellraw con colores/negritas/hex) ---
                List<OrderedText> richLines = null;
                int richColor = 0xFFFFFF;
                float richScale = 1.0f;
                if (type.equals("rich_text") && obj.has("message")) {
                    if (obj.has("color")) {
                        try {
                            richColor = TextColor.parse(obj.get("color").getAsString()).getOrThrow().getRgb();
                        } catch (Exception ignored) {}
                    }
                    try {
                        Text richText = parseRichText(obj.get("message"));
                        int maxWidth = obj.has("max_width") ? obj.get("max_width").getAsInt() : 300;
                        richLines = this.textRenderer.wrapLines(richText, maxWidth);

                        // NUEVO: si el mensaje es muy largo y se pasaría del alto disponible
                        // (max_height), lo achicamos proporcionalmente para que siempre quepa
                        // completo dentro de la caja, en vez de cortarse o desbordar.
                        int lineHeight = this.textRenderer.fontHeight + 2;
                        int totalHeight = richLines.size() * lineHeight;
                        if (obj.has("max_height")) {
                            int maxHeight = obj.get("max_height").getAsInt();
                            if (totalHeight > maxHeight && totalHeight > 0) {
                                richScale = (float) maxHeight / (float) totalHeight;
                                richScale = Math.max(0.45f, Math.min(1.0f, richScale));
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parseando rich_text '" + id + "': " + e.getMessage());
                        richLines = this.textRenderer.wrapLines(Text.literal("Error al leer el mensaje."), 300);
                    }
                }

                interactableElements.add(new GuiElement(
                        id, type, texture, mcItem, x, y, width, height, texWidth, texHeight,
                        isButton, tooltipLines, action, textContent, textColor, textScale, textBold,
                        entityId, entityName, entityScale, richLines, richColor, richScale
                ));
            }
        }
    }

    /**
     * Convierte un JsonElement estilo tellraw/vanilla (string, objeto {text,color,bold,...,extra:[...]}
     * o un array de componentes) en un Text de Minecraft con sus estilos aplicados.
     */
    private Text parseRichText(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Text.literal("");
        }

        if (element.isJsonPrimitive()) {
            return Text.literal(element.getAsString());
        }

        if (element.isJsonArray()) {
            MutableText result = Text.literal("");
            for (JsonElement child : element.getAsJsonArray()) {
                result.append(parseRichText(child));
            }
            return result;
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String txt = obj.has("text") ? obj.get("text").getAsString() : "";
            MutableText mt = Text.literal(txt);

            Style style = Style.EMPTY;
            if (obj.has("color")) {
                try {
                    style = style.withColor(TextColor.parse(obj.get("color").getAsString()).getOrThrow());
                } catch (Exception ignored) {}
            }
            if (obj.has("bold")) style = style.withBold(obj.get("bold").getAsBoolean());
            if (obj.has("italic")) style = style.withItalic(obj.get("italic").getAsBoolean());
            if (obj.has("underlined")) style = style.withUnderline(obj.get("underlined").getAsBoolean());
            if (obj.has("strikethrough")) style = style.withStrikethrough(obj.get("strikethrough").getAsBoolean());
            if (obj.has("obfuscated")) style = style.withObfuscated(obj.get("obfuscated").getAsBoolean());
            mt.setStyle(style);

            if (obj.has("extra")) {
                for (JsonElement child : obj.getAsJsonArray("extra")) {
                    mt.append(parseRichText(child));
                }
            }
            return mt;
        }

        return Text.literal("");
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

        float animScale;
        if (isClosing) {
            animScale = 1.0f - (progress * progress * progress);
            if (progress >= 1.0f) {
                super.close();
                return;
            }
        } else {
            animScale = 1.0f - (float) Math.pow(1.0f - progress, 3);
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

        if (bgTexture != null) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            int bgX = centerX - (bgWidth / 2);
            int bgY = centerY - (bgHeight / 2);
            context.drawTexture(bgTexture, bgX, bgY, 0, 0, bgWidth, bgHeight, bgTexWidth, bgTexHeight);
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

            if (element.type.equals("item_slot")) {
                if (element.texture != null) {
                    context.drawTexture(element.texture, element.x, element.y, 0, 0, element.width, element.height, element.texWidth, element.texHeight);
                }

                if (isHovered) {
                    context.fill(element.x + 2, element.y + 2, element.x + element.width - 2, element.y + element.height - 2, 0x80FFFFFF);
                }

                if (element.mcItem != null && !element.mcItem.isEmpty()) {
                    context.getMatrices().push();
                    float itemScale = (element.width - 8) / 16.0f;
                    context.getMatrices().translate(element.x + 4, element.y + 4, 100);
                    context.getMatrices().scale(itemScale, itemScale, 1.0f);

                    context.drawItem(element.mcItem, 0, 0);
                    context.getMatrices().pop();
                }

            } else if (element.type.equals("entity")) {
                // Dibujar fondo de tarjeta
                if (element.texture != null) {
                    context.drawTexture(element.texture, element.x, element.y, 0, 0, element.width, element.height, element.texWidth, element.texHeight);
                }

                if (isHovered) {
                    context.fill(element.x + 2, element.y + 2, element.x + element.width - 2, element.y + element.height - 2, 0x80FFFFFF);
                }

                // Renderizado en 3D de la Entidad
                LivingEntity entity = element.getOrCreateEntity(this.client);
                if (entity != null) {
                    // Forzar el "tick" para que las texturas/animaciones de Optifine y EMF/ETF se actualicen
                    if (this.client.world != null) {
                        entity.age = (int) this.client.world.getTime();
                    }

                    // 1. Quitar nuestra matriz temporalmente para entrar al contexto de pantalla global
                    context.getMatrices().pop();

                    // 2. Calcular la caja absoluta en pantalla aplicando nuestras matemáticas de escala
                    int absX1 = (int) (centerX + (element.x + 2 - centerX) * finalScale);
                    int absY1 = (int) (centerY + (element.y + 2 - centerY) * finalScale);
                    int absX2 = (int) (centerX + (element.x + element.width - 2 - centerX) * finalScale);
                    int absY2 = (int) (centerY + (element.y + element.height - 4 - centerY) * finalScale);
                    int scaledSize = (int) (element.entityScale * finalScale);

                    // 3. Activar el recorte (Scissor) para que los mobs gigantes no se salgan de su tarjeta
                    context.enableScissor(absX1, absY1, absX2, absY2);

                    // 4. Renderizamos la entidad (pasando el mouse para que lo siga con la mirada)
                    InventoryScreen.drawEntity(
                            context,
                            absX1,
                            absY1,
                            absX2,
                            absY2,
                            scaledSize,
                            0.0625f,
                            mouseX,
                            mouseY,
                            entity
                    );

                    // 5. Desactivar recorte
                    context.disableScissor();

                    // 6. Volver a aplicar la matriz para que siga renderizando el resto de la GUI normal
                    context.getMatrices().push();
                    context.getMatrices().translate(centerX, centerY, 0);
                    context.getMatrices().scale(finalScale, finalScale, 1.0f);
                    context.getMatrices().translate(-centerX, -centerY, 0);
                }

            } else if (element.type.equals("text")) {
                context.getMatrices().push();
                context.getMatrices().translate(element.x, element.y, 0);
                context.getMatrices().scale(element.textScale, element.textScale, 1.0f);

                MutableText renderText = Text.literal(element.text).setStyle(
                        Text.empty().getStyle().withBold(element.textBold)
                );
                context.drawCenteredTextWithShadow(this.textRenderer, renderText, 0, 0, element.textColor);

                context.getMatrices().pop();

            } else if (element.type.equals("rich_text")) {
                if (element.richLines != null) {
                    int lineHeight = this.textRenderer.fontHeight + 2;

                    context.getMatrices().push();
                    context.getMatrices().translate(element.x, element.y, 0);
                    context.getMatrices().scale(element.richScale, element.richScale, 1.0f);

                    int ly = 0;
                    for (OrderedText line : element.richLines) {
                        context.drawText(this.textRenderer, line, 0, ly, element.richColor, true);
                        ly += lineHeight;
                    }

                    context.getMatrices().pop();
                }
            } else if (element.texture != null && !element.type.equals("invisible_button")) {
                context.drawTexture(element.texture, element.x, element.y, 0, 0, element.width, element.height, element.texWidth, element.texHeight);
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

    private static class GuiElement {
        String id, type, action;
        Identifier texture;
        ItemStack mcItem;
        int x, y, width, height;
        int texWidth, texHeight;
        boolean isButton;
        List<Text> tooltipLines;

        // Campos de texto
        String text;
        int textColor;
        float textScale;
        boolean textBold;

        // Campos de entidad
        String entityId;
        String entityName;
        int entityScale;
        private LivingEntity cachedEntity = null;
        private boolean entityInitAttempted = false;

        // Campos de rich_text
        List<OrderedText> richLines;
        int richColor;
        float richScale;

        GuiElement(String id, String type, Identifier texture, ItemStack mcItem, int x, int y, int width, int height,
                   int texWidth, int texHeight, boolean isButton, List<Text> tooltipLines, String action,
                   String text, int textColor, float textScale, boolean textBold,
                   String entityId, String entityName, int entityScale,
                   List<OrderedText> richLines, int richColor, float richScale) {
            this.id = id; this.type = type; this.texture = texture; this.mcItem = mcItem;
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.texWidth = texWidth; this.texHeight = texHeight;
            this.isButton = isButton; this.tooltipLines = tooltipLines; this.action = action;
            this.text = text; this.textColor = textColor; this.textScale = textScale; this.textBold = textBold;
            this.entityId = entityId; this.entityName = entityName; this.entityScale = entityScale;
            this.richLines = richLines; this.richColor = richColor; this.richScale = richScale;
        }

        boolean isHovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        public LivingEntity getOrCreateEntity(MinecraftClient client) {
            if (entityInitAttempted) return cachedEntity;
            entityInitAttempted = true;

            if (entityId == null || client.world == null) return null;

            try {
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(Identifier.of(entityId));
                if (entityType != null) {
                    Entity entity = entityType.create(client.world);
                    if (entity instanceof LivingEntity le) {
                        cachedEntity = le;
                        cachedEntity.setUuid(UUID.randomUUID());
                        if (client.player != null) {
                            cachedEntity.setPosition(client.player.getX(), client.player.getY(), client.player.getZ());
                        } else {
                            cachedEntity.setPosition(0, client.world.getSeaLevel(), 0);
                        }

                        if (this.entityName != null && !this.entityName.isEmpty()) {
                            cachedEntity.setCustomName(Text.literal(this.entityName));
                            cachedEntity.setCustomNameVisible(false);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error al instanciar la entidad para la GUI: " + entityId);
            }
            return cachedEntity;
        }
    }
}