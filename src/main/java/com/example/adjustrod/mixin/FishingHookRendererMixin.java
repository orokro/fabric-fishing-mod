package com.example.adjustrod.mixin;

import com.example.adjustrod.AdjustRodConfig;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shifts the point the fishing line is drawn from.
 *
 * <p>{@code FishingHookRenderer#getPlayerHandPos(Player, float, float)} returns the world
 * position the line originates from (it is called from {@code extractRenderState}, which stores
 * {@code handPos - bobberPos} into the render state). By adding our offset to its return value we
 * move the visible start of the line to the tip of a custom rod model.
 *
 * <p>We deliberately do not capture the target method's parameters, so this stays robust even if
 * Mojang tweaks that private helper's signature between 26.1.x patches — it only needs the method
 * name to still be {@code getPlayerHandPos}.
 */
@Mixin(FishingHookRenderer.class)
public class FishingHookRendererMixin {

	@Inject(method = "getPlayerHandPos", at = @At("RETURN"), cancellable = true)
	private void adjustrod$offsetLineOrigin(CallbackInfoReturnable<Vec3> cir) {
		Vec3 base = cir.getReturnValue();
		if (base == null) {
			return;
		}

		Vec3 delta = AdjustRodConfig.worldDelta(1.0F);
		if (delta.lengthSqr() == 0.0) {
			return;
		}

		cir.setReturnValue(base.add(delta));
	}
}
