package com.example.adjustrod;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * EXPERIMENTAL "/adjustrod auto" — tries to find a custom rod's tip and turn it into an offset.
 *
 * <p>It reads the model JSON of the item you're holding straight from the resource packs (so your
 * custom pack is what gets inspected) and estimates where the rod's tip ends up on screen by
 * applying the model's {@code firstperson_righthand} display transform.
 *
 * <p>Two model styles are handled:
 * <ol>
 *     <li><b>3D cuboid models</b> ("elements"): the tip is taken as the element corner furthest
 *         from the model centre.</li>
 *     <li><b>Flat sprite rods</b> ({@code item/handheld} / {@code item/generated}): the tip is the
 *         non-transparent texture pixel furthest from the centre.</li>
 * </ol>
 *
 * <p>This is a <i>starting estimate</i>, not a perfect calibration — the vanilla line origin isn't
 * exactly the camera, so expect to nudge the result with {@code /adjustrod x y z}.
 */
public final class AutoTipEstimator {

	private AutoTipEstimator() {
	}

	public static void run(FabricClientCommandSource source) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) {
				source.sendError(Component.literal("[adjustrod] No player."));
				return;
			}

			ItemStack stack = mc.player.getMainHandItem();
			if (stack.isEmpty()) {
				stack = mc.player.getOffhandItem();
			}
			if (stack.isEmpty()) {
				source.sendError(Component.literal("[adjustrod] Hold your fishing rod, then run /adjustrod auto."));
				return;
			}

			Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
			ResourceManager rm = mc.getResourceManager();

			// Prefer the "_cast" model since that is what is shown while a line is out.
			JsonObject model = null;
			Identifier usedLoc = null;
			String[] candidates = {
					"models/item/" + itemId.getPath() + "_cast.json",
					"models/item/" + itemId.getPath() + ".json"
			};
			for (String path : candidates) {
				Identifier loc = Identifier.fromNamespaceAndPath(itemId.getNamespace(), path);
				Optional<Resource> res = rm.getResource(loc);
				if (res.isPresent()) {
					JsonObject candidate = readJson(res.get());
					if (candidate != null) {
						model = candidate;
						usedLoc = loc;
						if (candidate.has("elements")) {
							break;
						}
					}
				}
			}

			if (model == null) {
				source.sendError(Component.literal("[adjustrod] Couldn't find a model for " + itemId
						+ ". Set the offset manually with /adjustrod x y z."));
				return;
			}

			String ns = itemId.getNamespace();
			Vector3f tipModel; // tip position in 0..16 model space

			JsonObject elementsHolder = findWith(rm, model, ns, "elements", 0);
			if (elementsHolder != null) {
				tipModel = tipFromElements(elementsHolder.getAsJsonArray("elements"));
			} else {
				// Sprite-based rod: find the tip pixel in the layer0 texture.
				tipModel = tipFromTexture(rm, model, ns);
				if (tipModel == null) {
					source.sendError(Component.literal("[adjustrod] Couldn't read geometry or texture for " + itemId
							+ ". Set the offset manually with /adjustrod x y z."));
					return;
				}
			}

			// Resolve the firstperson_righthand display transform from the model (or its parents).
			float[] rot = {0, 0, 0};
			float[] trans = {0, 0, 0};
			float[] scale = {1, 1, 1};
			JsonObject displayHolder = findWith(rm, model, ns, "display", 0);
			if (displayHolder != null) {
				JsonObject display = displayHolder.getAsJsonObject("display");
				if (display.has("firstperson_righthand")) {
					JsonObject fp = display.getAsJsonObject("firstperson_righthand");
					if (fp.has("rotation")) rot = readVec(fp.getAsJsonArray("rotation"));
					if (fp.has("translation")) trans = readVec(fp.getAsJsonArray("translation"));
					if (fp.has("scale")) scale = readVec(fp.getAsJsonArray("scale"));
				}
			}

			// Model space (0..16) -> centred blocks (subtract 0.5 block, i.e. 8/16).
			Vector3f v = new Vector3f((tipModel.x - 8f) / 16f, (tipModel.y - 8f) / 16f, (tipModel.z - 8f) / 16f);
			// Display transform order applied to a point is: scale, then rotate, then translate.
			v.mul(scale[0], scale[1], scale[2]);
			Quaternionf q = new Quaternionf().rotationXYZ(
					(float) Math.toRadians(rot[0]),
					(float) Math.toRadians(rot[1]),
					(float) Math.toRadians(rot[2]));
			q.transform(v);
			v.add(trans[0] / 16f, trans[1] / 16f, trans[2] / 16f);

			// Map item-render space to our camera-relative offset:
			// +x ~ right, +y ~ up, -z ~ forward (the item points away from the camera).
			double right = round(v.x);
			double up = round(v.y);
			double forward = round(-v.z);

			AdjustRodConfig.set(right, up, forward);
			source.sendFeedback(Component.literal(String.format(
					"[adjustrod] AUTO estimate from %s -> right=%.3f, up=%.3f, forward=%.3f (saved). "
							+ "This is a starting point; fine-tune with /adjustrod x y z.",
					usedLoc, right, up, forward)));
		} catch (Exception e) {
			AdjustRodConfig.LOGGER.error("[adjustrod] auto failed", e);
			source.sendError(Component.literal("[adjustrod] Auto detection failed: " + e.getMessage()
					+ ". Set the offset manually with /adjustrod x y z."));
		}
	}

	/** The element corner furthest from the model centre (8,8,8). */
	private static Vector3f tipFromElements(JsonArray elements) {
		double best = -1;
		Vector3f tip = new Vector3f(8, 8, 8);
		for (int i = 0; i < elements.size(); i++) {
			JsonObject el = elements.get(i).getAsJsonObject();
			float[] from = readVec(el.getAsJsonArray("from"));
			float[] to = readVec(el.getAsJsonArray("to"));
			float[][] axis = {{from[0], to[0]}, {from[1], to[1]}, {from[2], to[2]}};
			for (int a = 0; a < 2; a++) {
				for (int b = 0; b < 2; b++) {
					for (int c = 0; c < 2; c++) {
						float cx = axis[0][a];
						float cy = axis[1][b];
						float cz = axis[2][c];
						double d = sq(cx - 8) + sq(cy - 8) + sq(cz - 8);
						if (d > best) {
							best = d;
							tip.set(cx, cy, cz);
						}
					}
				}
			}
		}
		return tip;
	}

	/** Non-transparent texture pixel furthest from centre, mapped into 0..16 model space. */
	private static Vector3f tipFromTexture(ResourceManager rm, JsonObject model, String ns) {
		String layer0 = findTexture(rm, model, ns, 0);
		if (layer0 == null) {
			return null;
		}
		Identifier tex = Identifier.parse(layer0);
		Identifier texLoc = Identifier.fromNamespaceAndPath(tex.getNamespace(), "textures/" + tex.getPath() + ".png");
		Optional<Resource> res = rm.getResource(texLoc);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream is = res.get().open()) {
			BufferedImage img = ImageIO.read(is);
			if (img == null) {
				return null;
			}
			int w = img.getWidth();
			// Animated textures are taller than wide; only inspect the first (square) frame.
			int h = (img.getHeight() > w && img.getHeight() % w == 0) ? w : img.getHeight();
			double cx = w / 2.0;
			double cy = h / 2.0;
			double best = -1;
			int tu = w / 2;
			int tv = h / 2;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int argb = img.getRGB(x, y);
					int alpha = (argb >>> 24) & 0xFF;
					if (alpha <= 16) {
						continue;
					}
					double d = sq(x + 0.5 - cx) + sq(y + 0.5 - cy);
					if (d > best) {
						best = d;
						tu = x;
						tv = y;
					}
				}
			}
			// Sprite -> model space: u maps to +x, the image's top (v=0) maps to +y (model y is up).
			float mx = (float) ((tu + 0.5) / w * 16.0);
			float my = (float) ((h - 1 - tv + 0.5) / h * 16.0);
			return new Vector3f(mx, my, 8f);
		} catch (Exception e) {
			return null;
		}
	}

	/** Walk the parent chain until a model declaring {@code key} is found. */
	private static JsonObject findWith(ResourceManager rm, JsonObject model, String ns, String key, int depth) {
		if (model == null || depth > 8) {
			return null;
		}
		if (model.has(key)) {
			return model;
		}
		if (model.has("parent")) {
			return findWith(rm, loadModel(rm, model.get("parent").getAsString()), ns, key, depth + 1);
		}
		return null;
	}

	/** Resolve the layer0 texture id, following parents if needed. */
	private static String findTexture(ResourceManager rm, JsonObject model, String ns, int depth) {
		if (model == null || depth > 8) {
			return null;
		}
		if (model.has("textures")) {
			JsonObject textures = model.getAsJsonObject("textures");
			if (textures.has("layer0")) {
				return textures.get("layer0").getAsString();
			}
		}
		if (model.has("parent")) {
			return findTexture(rm, loadModel(rm, model.get("parent").getAsString()), ns, depth + 1);
		}
		return null;
	}

	private static JsonObject loadModel(ResourceManager rm, String parentRef) {
		if (parentRef == null || parentRef.startsWith("builtin/")) {
			return null;
		}
		Identifier ref = Identifier.parse(parentRef);
		Identifier loc = Identifier.fromNamespaceAndPath(ref.getNamespace(), "models/" + ref.getPath() + ".json");
		Optional<Resource> res = rm.getResource(loc);
		return res.map(AutoTipEstimator::readJson).orElse(null);
	}

	private static JsonObject readJson(Resource resource) {
		try (InputStream is = resource.open();
			 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	private static float[] readVec(JsonArray arr) {
		float[] out = new float[Math.max(3, arr.size())];
		for (int i = 0; i < arr.size(); i++) {
			out[i] = arr.get(i).getAsFloat();
		}
		return out;
	}

	private static double sq(double x) {
		return x * x;
	}

	private static double round(double x) {
		return Math.round(x * 1000.0) / 1000.0;
	}
}
