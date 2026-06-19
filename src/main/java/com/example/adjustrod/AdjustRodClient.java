package com.example.adjustrod;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * Client entry point. Loads the saved offsets and registers the {@code /adjustrod} command.
 *
 * <ul>
 *     <li>{@code /adjustrod <x> <y> <z>} — set the FIRST-person offset (right, up, forward) and save</li>
 *     <li>{@code /adjustrod third <x> <y> <z>} — set the THIRD-person offset and save</li>
 *     <li>{@code /adjustrod show} — print both offsets</li>
 *     <li>{@code /adjustrod reset} — set both offsets back to 0 0 0</li>
 *     <li>{@code /adjustrod auto} — best-effort first-person estimate from the held rod's model (experimental)</li>
 * </ul>
 */
public class AdjustRodClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		AdjustRodConfig.load();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommands.literal("adjustrod")
						.then(ClientCommands.literal("show")
								.executes(ctx -> {
									show(ctx.getSource());
									return 1;
								}))
						.then(ClientCommands.literal("reset")
								.executes(ctx -> {
									AdjustRodConfig.reset();
									ctx.getSource().sendFeedback(Component.literal("[adjustrod] All offsets reset to 0, 0, 0."));
									return 1;
								}))
						.then(ClientCommands.literal("auto")
								.executes(ctx -> {
									AutoTipEstimator.run(ctx.getSource());
									return 1;
								}))
						.then(ClientCommands.literal("third")
								.then(ClientCommands.argument("x", DoubleArgumentType.doubleArg())
										.then(ClientCommands.argument("y", DoubleArgumentType.doubleArg())
												.then(ClientCommands.argument("z", DoubleArgumentType.doubleArg())
														.executes(ctx -> {
															double x = DoubleArgumentType.getDouble(ctx, "x");
															double y = DoubleArgumentType.getDouble(ctx, "y");
															double z = DoubleArgumentType.getDouble(ctx, "z");
															AdjustRodConfig.setThirdPerson(x, y, z);
															ctx.getSource().sendFeedback(Component.literal(String.format(
																	"[adjustrod] Third-person offset set to right=%.3f, up=%.3f, forward=%.3f (saved, live).", x, y, z)));
															return 1;
														})))))
						.then(ClientCommands.argument("x", DoubleArgumentType.doubleArg())
								.then(ClientCommands.argument("y", DoubleArgumentType.doubleArg())
										.then(ClientCommands.argument("z", DoubleArgumentType.doubleArg())
												.executes(ctx -> {
													double x = DoubleArgumentType.getDouble(ctx, "x");
													double y = DoubleArgumentType.getDouble(ctx, "y");
													double z = DoubleArgumentType.getDouble(ctx, "z");
													AdjustRodConfig.setFirstPerson(x, y, z);
													ctx.getSource().sendFeedback(Component.literal(String.format(
															"[adjustrod] First-person offset set to right=%.3f, up=%.3f, forward=%.3f (saved, live).", x, y, z)));
													return 1;
												}))))));
	}

	private static void show(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal(String.format(
				"[adjustrod] First-person: right=%.3f, up=%.3f, forward=%.3f  |  Third-person: right=%.3f, up=%.3f, forward=%.3f",
				AdjustRodConfig.fpRight(), AdjustRodConfig.fpUp(), AdjustRodConfig.fpForward(),
				AdjustRodConfig.tpRight(), AdjustRodConfig.tpUp(), AdjustRodConfig.tpForward())));
	}
}
