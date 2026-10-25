package net.hugosmp.auction;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HugoAuctionClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("hugoauction");
    public static final AuctionManager MANAGER = new AuctionManager();
    public static AuctionConfig CONFIG;

    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        CONFIG = AuctionConfig.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hugoauction.open",
                InputUtil.Type.KEYSYM,
                74, // J
                KeyBinding.Category.create(Identifier.of("hugoauction", "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(MANAGER.isRunning() ? new AuctionLiveScreen() : new AuctionScreen());
                }
            }
            MANAGER.tick();
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) MANAGER.onSystemMessage(message.getString());
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.of("hugoauction", "auction_hud"),
                AuctionHud::render);
    }
}
