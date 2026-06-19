package com.example.adjustrod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Holds the rod offset and persists it to <code>config/adjustrod.json</code>.
 *
 * <p>The offset is stored in <b>camera-relative</b> space (so it tracks your view):
 * <ul>
 *     <li><b>right</b> (x) — positive moves the line origin to the right of where you look</li>
 *     <li><b>up</b> (y) — positive moves it up</li>
 *     <li><b>forward</b> (z) — positive pushes it further out along your look direction</li>
 * </ul>
 * Values are in blocks (1.0 == one block). Typical tuning values are small, e.g. 0.1.
 *
 * <p>Because the mixin reads {@link #worldDelta(float)} every frame, changes made with the
 * command apply <b>live</b> — no relog or restart needed.
 */
public final class AdjustRodConfig {
	public static final Logger LOGGER = LoggerFactory.getLogger("adjustrod");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("adjustrod.json");

	// volatile: written from the client/command thread, read from the render thread.
	private static volatile double offsetRight = 0.0;
	private static volatile double offsetUp = 0.0;
	private static volatile double offsetForward = 0.0;

	private AdjustRodConfig() {
	}

	/** Simple serialization holder. */
	private static final class Data {
		double right;
		double up;
		double forward;
	}

	public static void load() {
		try {
			if (Files.exists(CONFIG_PATH)) {
				String json = Files.readString(CONFIG_PATH);
				Data d = GSON.fromJson(json, Data.class);
				if (d != null) {
					offsetRight = d.right;
					offsetUp = d.up;
					offsetForward = d.forward;
				}
				LOGGER.info("[adjustrod] Loaded offset right={}, up={}, forward={}", offsetRight, offsetUp, offsetForward);
			} else {
				save();
				LOGGER.info("[adjustrod] Created default config at {}", CONFIG_PATH);
			}
		} catch (Exception e) {
			LOGGER.error("[adjustrod] Failed to load config, using zero offset", e);
		}
	}

	public static void save() {
		try {
			Data d = new Data();
			d.right = offsetRight;
			d.up = offsetUp;
			d.forward = offsetForward;
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, GSON.toJson(d));
		} catch (IOException e) {
			LOGGER.error("[adjustrod] Failed to save config", e);
		}
	}

	public static void set(double right, double up, double forward) {
		offsetRight = right;
		offsetUp = up;
		offsetForward = forward;
		save();
	}

	public static double right() {
		return offsetRight;
	}

	public static double up() {
		return offsetUp;
	}

	public static double forward() {
		return offsetForward;
	}

	/**
	 * Converts the stored camera-relative offset into a world-space delta using the local
	 * player's current view rotation. Returns {@link Vec3#ZERO} when no offset is set or no
	 * player is available, so the vanilla behaviour is untouched.
	 */
	public static Vec3 worldDelta(float partialTick) {
		if (offsetRight == 0.0 && offsetUp == 0.0 && offsetForward == 0.0) {
			return Vec3.ZERO;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return Vec3.ZERO;
		}

		float pitch = mc.player.getViewXRot(partialTick);
		float yaw = mc.player.getViewYRot(partialTick);

		// Build an orthonormal camera basis from the view angles.
		Vec3 forward = Vec3.directionFromRotation(pitch, yaw).normalize();
		Vec3 up = Vec3.directionFromRotation(pitch - 90.0F, yaw).normalize();
		Vec3 right = forward.cross(up).normalize();

		return right.scale(offsetRight)
				.add(up.scale(offsetUp))
				.add(forward.scale(offsetForward));
	}
}
