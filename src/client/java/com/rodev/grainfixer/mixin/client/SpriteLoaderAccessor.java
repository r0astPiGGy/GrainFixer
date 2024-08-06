package com.rodev.grainfixer.mixin.client;

import net.minecraft.client.texture.SpriteLoader;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteLoader.class)
public interface SpriteLoaderAccessor {

    @Accessor("LOGGER")
    static Logger getLogger() {
        throw new IllegalStateException();
    }

}
