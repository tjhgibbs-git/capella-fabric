package io.github.tjhgibbs.capella.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.tjhgibbs.capella.mod.CapellaMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Client entrypoint: the key that opens the generator screen (H by default; Sylvan, the tree generator, has G). */
public final class CapellaClient implements ClientModInitializer {
    static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(CapellaMod.MOD_ID, "main"));
    static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.capella.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            // Key mappings only register clicks while no screen has the input, so this cannot
            // open the generator on top of another screen.
            while (openKey.consumeClick()) {
                mc.setScreenAndShow(new GeneratorScreen(null));
            }
        });
    }
}
