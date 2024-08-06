package com.rodev.grainfixer.mixin.client;

import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.client.texture.SpriteOpener;
import net.minecraft.client.texture.atlas.AtlasLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

@Mixin(SpriteLoader.class)
public class SpriteLoaderMixin {

    @Unique
    private boolean filterSprite(SpriteContents sprite, int mipLevel) {

        int k = 1 << mipLevel;
        int j = Math.min(Integer.MAX_VALUE, Math.min(sprite.getWidth(), sprite.getHeight()));
        int l = Math.min(Integer.lowestOneBit(sprite.getWidth()), Integer.lowestOneBit(sprite.getHeight()));

        if (l < k) {
            SpriteLoaderAccessor.getLogger().info("Skipping texture {} with size {}x{} as it limits mip level from {} to {}", sprite.getId(), sprite.getWidth(), sprite.getHeight(), MathHelper.floorLog2(k), MathHelper.floorLog2(l));
            return false;
        }

        return true;
    }

    @Unique
    private CompletableFuture<List<SpriteContents>> loadAll(
            SpriteOpener opener,
            List<Function<SpriteOpener, SpriteContents>> sources,
            Executor executor,
            int mipLevel
    ) {
        List<CompletableFuture<SpriteContents>> list = sources.stream()
                .map(sprite -> CompletableFuture.supplyAsync(() -> sprite.apply(opener), executor))
                .toList();
        return Util.combineSafe(list).thenApply(sprites -> sprites.stream().filter(sp -> Objects.nonNull(sp) && filterSprite(sp, mipLevel)).toList());
    }

    @Inject(
            method = "Lnet/minecraft/client/texture/SpriteLoader;load(Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/Identifier;ILjava/util/concurrent/Executor;Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mixinLoad(
            ResourceManager resourceManager,
            Identifier path,
            int mipLevel,
            Executor executor,
            Collection<ResourceMetadataReader<?>> metadatas,
            CallbackInfoReturnable<CompletableFuture<SpriteLoader.StitchResult>> cir
    ) {
        SpriteOpener spriteOpener = SpriteOpener.create(metadatas);
        SpriteLoader instance = (SpriteLoader) (Object) this;

        var value = CompletableFuture.supplyAsync(() -> AtlasLoader.of(resourceManager, path).loadSources(resourceManager), executor)
                .thenCompose(sources -> loadAll(spriteOpener, sources, executor, mipLevel))
                .thenApply(sprites -> instance.stitch(sprites, mipLevel, executor));

        cir.setReturnValue(value);
    }

}
