package com.crissyjuanxd.viciontguis.client.gui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * FIX del lag de ~2s la primera vez que se abre/cierra una GUI dinámica.
 * <p>
 * La causa real tiene DOS partes, no solo texturas:
 * 1) {@code TextureManager} sube cada PNG a la GPU recién la primera vez que algo la
 *    dibuja (bind perezoso).
 * 2) {@code EntityRenderDispatcher} bakea el renderer + modelo de un tipo de entidad
 *    recién la primera vez que se dibuja UNA entidad de ese tipo — esto es lo más caro
 *    de las dos, y es lo que explica que el menú de "entidades" (que dibuja 3 mobs de
 *    golpe) sea el que más lagea la primera vez.
 * <p>
 * Ambas cosas quedan cacheadas después, por eso el bug es "solo la primera vez".
 * <p>
 * El fix: tocar ambas cosas de antemano, en un momento donde el jugador ya espera algo
 * de carga (arranque del juego / al hacer join al mundo) en vez de en el primer frame
 * de la GUI.
 */
public final class GuiTexturePreloader implements IdentifiableResourceReloadListener {

    private static final GuiTexturePreloader INSTANCE = new GuiTexturePreloader();
    private static final Set<Identifier> KNOWN_TEXTURES = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final Set<Identifier> KNOWN_ENTITY_TYPES = Collections.synchronizedSet(new LinkedHashSet<>());

    private GuiTexturePreloader() {}

    public static void init() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(INSTANCE);
        ClientLifecycleEvents.CLIENT_STARTED.register(GuiTexturePreloader::warmAllKnown);

        // Las entidades necesitan un World para poder instanciarse, así que ese warm-up
        // recién puede pasar al hacer join (no al arrancar el cliente).
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> warmEntities(client));
    }

    /** Registra una textura para que se precargue en el próximo warm-up. */
    public static void remember(Identifier texture) {
        if (texture != null) {
            KNOWN_TEXTURES.add(texture);
        }
    }

    /** Registra un tipo de entidad (ej. "minecraft:zombie") para bakear su renderer de antemano. */
    public static void rememberEntity(Identifier entityTypeId) {
        if (entityTypeId != null) {
            KNOWN_ENTITY_TYPES.add(entityTypeId);
        }
    }

    /** Llamar apenas se reciba un payload del plugin, antes de que el jugador abra la GUI. */
    public static void warmAll(Iterable<Identifier> textures) {
        for (Identifier texture : textures) {
            remember(texture);
        }
        warmAllKnown(MinecraftClient.getInstance());
    }

    /** Fuerza un warm-up inmediato de todo lo que ya se conoce hasta ahora. */
    public static void warmKnown() {
        MinecraftClient client = MinecraftClient.getInstance();
        warmAllKnown(client);
        warmEntities(client);
    }

    private static void warmAllKnown(MinecraftClient client) {
        if (client == null) return;
        client.execute(() -> {
            for (Identifier texture : KNOWN_TEXTURES) {
                try {
                    client.getTextureManager().getTexture(texture);
                } catch (Exception ignored) {
                    // Textura inexistente o inválida: no rompemos el warm-up por una sola.
                }
            }
        });
    }

    private static void warmEntities(MinecraftClient client) {
        if (client == null || client.world == null) return;
        client.execute(() -> {
            for (Identifier entityTypeId : KNOWN_ENTITY_TYPES) {
                try {
                    EntityType<?> type = Registries.ENTITY_TYPE.get(entityTypeId);
                    Entity entity = type.create(client.world);
                    if (entity != null) {
                        // Tocar el renderer es lo que efectivamente bakea el modelo/layers.
                        client.getEntityRenderDispatcher().getRenderer(entity);
                    }
                } catch (Exception ignored) {
                    // Entidad inexistente o inválida: no rompemos el warm-up por una sola.
                }
            }
        });
    }

    @Override
    public Identifier getFabricId() {
        return Identifier.of("viciontguis", "gui_texture_preloader");
    }

    @Override
    public CompletableFuture<Void> reload(Synchronizer synchronizer, ResourceManager manager,
                                          Profiler prepareProfiler, Profiler applyProfiler,
                                          Executor prepareExecutor, Executor applyExecutor) {
        return CompletableFuture.completedFuture(null)
                .thenCompose(synchronizer::whenPrepared)
                .thenRunAsync(() -> warmAllKnown(MinecraftClient.getInstance()), applyExecutor);
    }
}