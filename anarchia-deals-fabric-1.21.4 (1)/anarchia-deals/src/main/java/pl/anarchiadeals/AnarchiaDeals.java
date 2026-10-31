package pl.anarchiadeals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnarchiaDeals implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("anarchiadeals");
    public static KeyBinding menuKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        PriceDb.load();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.anarchiadeals.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "category.anarchiadeals"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null && !Scanner.running()) {
                    client.setScreen(new DealScreen());
                }
            }
            Scanner.tick(client);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            Config.save();
            PriceDb.save();
        });
    }
}
