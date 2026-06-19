# AdjustRod

A small **client-side Fabric mod for Minecraft 26.1.1** that lets you nudge the point the fishing
line is cast from, so it lines up with the tip of a custom fishing-rod model instead of vanilla's
hard-coded hand position.

You tune it live in-game with a command, and the value is saved so it persists across restarts.

```
/adjustrod <x> <y> <z>         set the FIRST-person offset (right, up, forward) — applies instantly and saves
/adjustrod third <x> <y> <z>   set the THIRD-person offset — applies instantly and saves
/adjustrod show                print both offsets
/adjustrod reset               set both back to 0 0 0
/adjustrod auto                experimental: estimate the first-person offset from your rod's model
```

---

## 1. What it actually does

When you cast, the game draws the line from a point returned by
`FishingHookRenderer.getPlayerHandPos(...)`. This mod adds your offset to that point via a Mixin, so
the line starts where *you* tell it to.

There are **two independent offsets**, because the rod is positioned differently depending on the view:

- **First person** (`/adjustrod x y z`) — the rod is attached to the camera, so this offset is
  applied in a **camera-relative** frame: `x` = right of where you look, `y` = up, `z` = forward along
  your look direction. It's rotated by your view every frame, so it tracks as you look around.
- **Third person** (`/adjustrod third x y z`) — the rod hangs off your body, so this offset is applied
  in a **body-relative** frame: `x` = right of your body's facing, `y` = world up, `z` = forward of your
  body's facing. This matches how vanilla anchors the line to the body, so it tracks as you turn.

Units are blocks, so the numbers are small — `0.1` is already a noticeable shift. The two offsets are
saved separately, and `worldDelta` automatically picks the right one based on the active camera.

> A single offset per view can't be *perfect* in every situation (the vanilla origin isn't exactly at
> the camera, and the third-person value keys off the body rather than the live arm-swing animation),
> but it gets the line visually attached to the tip. Tune first person looking roughly level; tune
> third person while standing still.

---

## 2. Prerequisites (read this — 26.1 changed the toolchain)

Minecraft 26.1 is the first **unobfuscated** version, so the modding stack is newer than older guides:

| Tool | Version |
|------|---------|
| **JDK** | **25** (required — 26.1 needs Java 25) |
| **IntelliJ IDEA** | **2025.3 or newer** (required for Java 25 + Mixins to work) |
| Gradle | 9.4.1 (handled by the wrapper config — IntelliJ will download it) |
| Fabric Loom | `1.16-SNAPSHOT` (the new `net.fabricmc.fabric-loom` plugin, no remapping) |
| Fabric Loader | 0.18.6 |
| Fabric API | `0.145.4+26.1.1` |

### Install JDK 25
Easiest from inside IntelliJ: **File → Project Structure → SDKs → +  → Download JDK → version 25**
(Temurin/Adoptium or Oracle are both fine). Or download it yourself from adoptium.net.

---

## 3. Open & build in IntelliJ

1. **Open the folder** `fabric-fishing-mod` in IntelliJ (`File → Open…`, pick the folder, **Open as Project**). It will be recognized as a Gradle project.
2. When prompted to trust/import the Gradle project, accept. IntelliJ reads `gradle/wrapper/gradle-wrapper.properties` and downloads Gradle 9.4.1 automatically.
3. Point Gradle at JDK 25: **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM → 25**.
4. Set the project SDK + language level to 25: **File → Project Structure → Project → SDK = 25, Language level = 25**.
5. Let Gradle sync (the elephant/refresh icon in the Gradle tool window). First sync downloads Minecraft 26.1.1 + Fabric — give it a few minutes.
6. Build the jar: in the **Gradle** tool window run **Tasks → build → build**, or run `build` from the search-anything (double-Shift) box.

The finished mod is at:

```
build/libs/adjustrod-1.0.0.jar
```

(Ignore the `-sources.jar` — you want the plain one.)

> **No `gradlew` script is bundled** (the binary wrapper jar can't ship in this template). IntelliJ
> doesn't need it — it drives Gradle itself. If you *want* a command-line `./gradlew`, run once:
> `gradle wrapper --gradle-version 9.4.1` (needs a system Gradle install), then `./gradlew build`.

### Run it in the dev environment (optional)
Loom generates run configs after the first sync — pick **Minecraft Client** from the Run dropdown to
launch a dev client with the mod loaded, handy for testing without copying the jar.

---

## 4. Install & use

1. Make sure **Fabric Loader 0.18.6+** and **Fabric API `0.145.4+26.1.1`** are installed for 26.1.1.
2. Drop `adjustrod-1.0.0.jar` into your `.minecraft/mods` folder (alongside Fabric API).
3. Launch, load a world, hold your rod and cast.
4. Tune first person:
   ```
   /adjustrod 0.05 0.1 0.2
   ```
   The change is live — you'll see the next rendered frame use it. Cast again if a bobber is already out.
5. Iterate `x y z` until the line leaves the rod tip.
6. (Optional) Switch to third person and tune that view separately, standing still:
   ```
   /adjustrod third 0.0 0.1 0.3
   ```
7. `/adjustrod show` prints both offsets; `/adjustrod reset` zeroes both.

Both values are stored in `.minecraft/config/adjustrod.json` and reloaded on every boot.

---

## 5. The `auto` command (experimental / bonus)

`/adjustrod auto` reads the **model of the rod you're holding** from your resource pack and estimates
an offset:

- For a **true 3D model** (one with `elements`), it takes the corner furthest from the model centre as the tip.
- For a **flat sprite rod** (`item/handheld` style — the most common kind), it finds the furthest non-transparent pixel in the texture.
- It then applies the model's `firstperson_righthand` display transform (scale → rotate → translate) to estimate where that tip sits on screen, and saves that as your offset.

Treat the result as a **starting point**, then fine-tune with `/adjustrod x y z`. If the model uses a
format it can't parse, it says so and leaves you to set the offset manually. It never crashes the game
— failures just print a chat message.

---

## 6. Troubleshooting

**Gradle sync fails / wrong versions:** the exact recommended versions live at <https://fabricmc.net/develop>.
All versions are in `gradle.properties` — bump `minecraft_version`, `loader_version`,
`fabric_api_version`, `loom_version` there if you're on a different patch (e.g. 26.1.2). Fabric API
`0.152.1+26.1.2` is the 26.1.2 build, for reference.

**The mod loads but the line doesn't move:** the Mixin targets the private helper
`FishingHookRenderer.getPlayerHandPos`. If a future patch renames it you'll see a Mixin error in the
log mentioning that method. Fixes:
- Look up the current name (the line-origin helper) on <https://mcsrc.dev> and update `method = "getPlayerHandPos"` in `FishingHookRendererMixin.java`.
- If the log says the target method *is static*, add `static` to the `adjustrod$offsetLineOrigin` handler (or remove it if it complains the other way). It's currently written for an instance method.

**`/adjustrod` doesn't exist:** make sure Fabric API is installed — the client command system comes from it.

---

## 7. Project layout

```
fabric-fishing-mod/
├─ build.gradle              # Loom 1.16 (unobfuscated), Java 25
├─ settings.gradle
├─ gradle.properties         # all versions live here
├─ gradle/wrapper/gradle-wrapper.properties
└─ src/main/
   ├─ java/com/example/adjustrod/
   │  ├─ AdjustRodClient.java          # entry point + /adjustrod command
   │  ├─ AdjustRodConfig.java          # offset state, JSON save/load, camera→world math
   │  ├─ AutoTipEstimator.java         # /adjustrod auto (model inspection)
   │  └─ mixin/FishingHookRendererMixin.java
   └─ resources/
      ├─ fabric.mod.json
      └─ adjustrod.mixins.json
```

Rename the `com.example.adjustrod` package / `maven_group` if you publish it.
