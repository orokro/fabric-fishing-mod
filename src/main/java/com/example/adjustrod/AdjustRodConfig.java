package com.example.adjustrod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Holds the rod offsets and persists them to <code>config/adjustrod.json</code>.
 *
 * <p>There are two independent offsets, because the rod is positioned differently per view:
 * <ul>
 *     <li><b>First person</b> — the rod is attached to the camera, so the offset is applied in a
 *         <i>camera-relative</i> frame (x = right of where you look, y = up, z = forward along look).</li>
 *     <li><b>Third person</b> — the rod hangs off the player's body, so the offset is applied in a
 *         <i>body-relative</i> frame (x = right of the body's facing, y = world up, z = forward of the
 *         body's facing). This matches how vanilla derives the line's hand position from the body yaw.</li>
 * </ul>
 * Values are in blocks. Because the mixin reads {@link #worldDelta(float)} every frame, changes apply
 * live with no relog.
 */
public final class AdjustRodConfig {
	public static final Logger LOGGER = LoggerFactory.getLogger("adjustrod");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("adjustrod.json");

	// First-person offset (camera-relative).
	private static volatile double fpRight = 0.0;
	private static volatile double fpUp = 0.0;
	private static volatile double fpForward = 0.0;

	// Third-person offset (body-relative).
	private static volatile double tpRight = 0.0;
	private static volatile double tpUp = 0.0;
	private static volatile double tpForward = 0.0;

	private AdjustRodConfig() {
	}

	/** Serialization holder. The first-person fields keep their original names for backwards compatibility. */
	private static final class Data {
		double right;
		double up;
		double forward;
		double thirdRight;
		double thirdUp;
		double thirdForward;
	}

	public static void load() {
		try {
			if (Files.exists(CONFIG_PATH)) {
				Data d = GSON.fromJson(Files.readString(CONFIG_PATH), Data.class);
				if (d != null) {
					fpRight = d.right;
					fpUp = d.up;
					fpForward = d.forward;
					tpRight = d.thirdRight;
					tpUp = d.thirdUp;
					tpForward = d.thirdForward;
				}
				LOGGER.info("[adjustrod] Loaded first-person={},{},{} third-person={},{},{}",
						fpRight, fpUp, fpForward, tpRight, tpUp, tpForward);
			} else {
				save();
				LOGGER.info("[adjustrod] Created default config at {}", CONFIG_PATH);
			}
		} catch (Exception e) {
			LOGGER.error("[adjustrod] Failed to load config, using zero offsets", e);
		}
	}

	public static void save() {
		try {
			Data d = new Data();
			d.right = fpRight;
			d.up = fpUp;
			d.forward = fpForward;
			d.thirdRight = tpRight;
			d.thirdUp = tpUp;
			d.thirdForward = tpForward;
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, GSON.toJson(d));
		} catch (IOException e) {
			LOGGER.error("[adjustrod] Failed to save config", e);
		}
	}

	public static void setFirstPerson(double right, double up, double forward) {
		fpRight = right;
		fpUp = up;
		fpForward = forward;
		save();
	}

	public static void setThirdPerson(double right, double up, double forward) {
		tpRight = right;
		tpUp = up;
		tpForward = forward;
		save();
	}

	public static void reset() {
		fpRight = fpUp = fpForward = 0.0;
		tpRight = tpUp = tpForward = 0.0;
		save();
	}

	public static double fpRight() {
		return fpRight;
	}

	public static double fpUp() {
		return fpUp;
	}

	public static double fpForward() {
		return fpForward;
	}

	public static double tpRight() {
		return tpRight;
	}

	public static double tpUp() {
		return tpUp;
	}

	public static double tpForward() {
		return tpForward;
	}

	/**
	 * Returns the world-space delta to add to the line origin, picking the right frame for the
	 * current camera. Returns {@link Vec3#ZERO} when no offset applies, so vanilla behaviour is
	 * untouched.
	 */
	public static Vec3 worldDelta(float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options == null) {
			return Vec3.ZERO;
		}

		if (mc.options.getCameraType().isFirstPerson()) {
			if (fpRight == 0.0 && fpUp == 0.0 && fpForward == 0.0) {
				return Vec3.ZERO;
			}
			// Camera-relative frame (tracks where you look).
			float pitch = mc.player.getViewXRot(partialTick);
			float yaw = mc.player.getViewYRot(partialTick);
			Vec3 forward = Vec3.directionFromRotation(pitch, yaw).normalize();
			Vec3 up = Vec3.directionFromRotation(pitch - 90.0F, yaw).normalize();
			Vec3 right = forward.cross(up).normalize();
			return right.scale(fpRight).add(up.scale(fpUp)).add(forward.scale(fpForward));
		}

		// Third person: body-relative frame.
		if (tpRight == 0.0 && tpUp == 0.0 && tpForward == 0.0) {
			return Vec3.ZERO;
		}
		float bodyYaw = Mth.rotLerp(partialTick, mc.player.yBodyRotO, mc.player.yBodyRot);
		Vec3 forward = Vec3.directionFromRotation(0.0F, bodyYaw).normalize();
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		Vec3 right = forward.cross(up).normalize();
		return right.scale(tpRight).add(up.scale(tpUp)).add(forward.scale(tpForward));
	}
}
