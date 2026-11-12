package dev.spawnsight.client.mixin;

import dev.spawnsight.client.scan.Scanner;

import net.minecraft.client.renderer.extract.LevelExtractor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every reason the game redraws a section (block change, client light update, light packet, chunk
 * load) ends in this method, so it is also Spawnsight's "rescan this section" signal.
 */
@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
	@Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
	private void spawnsight$dirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged, CallbackInfo ci) {
		Scanner.markDirty(sectionX, sectionY, sectionZ);
	}
}
