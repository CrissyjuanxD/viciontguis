package com.crissyjuanxd.viciontguis.client.mixin;

import com.crissyjuanxd.viciontguis.client.ViciontGuisClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.SoundOptionsScreen;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class SoundOptionsScreenMixin {

    @Inject(method = "init", at = @At("RETURN"))
    private void onInitAddViciontVolume(CallbackInfo ci) {
        // Disfrazamos el Mixin como la pantalla actual (Screen)
        Screen screen = (Screen) (Object) this;

        // Verificamos si la pantalla actual es la de opciones de sonido
        if (screen instanceof SoundOptionsScreen) {

            SimpleOption<Double> viciontVolumeOption = new SimpleOption<>(
                    "Viciont Menu",
                    SimpleOption.emptyTooltip(),
                    (text, value) -> Text.literal("Viciont Menu: " + (int)(value * 100) + "%"),
                    SimpleOption.DoubleSliderCallbacks.INSTANCE,
                    (double) ViciontGuisClient.MENU_VOLUME,
                    value -> ViciontGuisClient.MENU_VOLUME = value.floatValue()
            );

            // Iteramos sobre todos los elementos interactuables de la pantalla
            for (Element child : screen.children()) {
                // Buscamos la lista nativa de Minecraft donde están guardadas las opciones
                if (child instanceof OptionListWidget listWidget) {
                    // Añadimos nuestro botón nativamente al final de la lista
                    listWidget.addSingleOptionEntry(viciontVolumeOption);
                    break;
                }
            }
        }
    }
}