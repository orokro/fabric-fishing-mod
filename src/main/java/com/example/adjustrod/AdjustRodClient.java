package com.example.adjustrod;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * Client entry point. Loads the saved offset and registers the {@code /adjustrod} command.
 *
 * <ul>
 *     <li>{@code /adjustrod <x> <y> <z>} — set the camera-relative offset (right, up, forward) and save it</li>
 *     <li>{@code /adjustrod show} — print the current offset</li>
 *     <li>{@code /adjustrod reset} — set the offset back to 0 0 0</li>
 *     <li>{@code /adjustrod auto} — best-effort estimate from the held rod's model (experimental)</li>
 * </ul>
 */
public class AdjustRodClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		AdjustRodConfig.load();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("adjustrod")
						.then(ClientCommandManager.literal("show")
								.executes(ctx -> {
									show(ctx.getSource());
									return 1;
								}))
						.then(ClientCommandManager.literal("reset")
								.executes(ctx -> {
									AdjustRodConfig.set(0.0, 0.0, 0.0);
									ctx.getSource().sendFeedback(Component.literal("[adjustrod] Offset reset to 0, 0, 0."));
									return 1;
								}))
						.then(ClientCommandManager.literal("auto")
								.executes(ctx -> {
									AutoTipEstimator.run(ctx.getSource());
									return 1;
								}))
						.then(ClientCommandManager.argument("x", DoubleArgumentType.doubleArg())
								.then(ClientCommandManager.argument("y", DoubleArgumentType.doubleArg())
										.then(ClientCommandManager.argument("z", DoubleArgumentType.doubleArg())
												.executes(ctx -> {
													double x = DoubleArgumentType.getDouble(ctx, "x");
													double y = DoubleArgumentType.getDouble(ctx, "y");
													double z = DoubleArgumentType.getDouble(ctx, "z");
													AdjustRodConfig.set(x, y, z);
													ctx.getSource().sendFeedback(Component.literal(String.format(
															"[adjustrod] Offset set to right=%.3f, up=%.3f, forward=%.3f (saved, live).", x, y, z)));
													return 1;
												}))))));
	}

	private static void show(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal(String.format(
				"[adjustrod] Current offset: right=%.3f, up=%.3f, forward=%.3f",
				AdjustRodConfig.right(), AdjustRodConfig.up(), AdjustRodConfig.forward())));
	}
}
