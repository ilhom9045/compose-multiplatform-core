# Guest Shim — Vertical Slice (Column / Row / Box) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In this repository, build a `guest-shim` module that declares Compose's public API under its real `androidx.compose.*` package names but emits render nodes instead of measuring and drawing — proven end to end by running the compiled guest inside QuickJS from a JVM test, with `Column`, `Row` and `Box` as the slice.

**Architecture:** `guest-shim` is a top-level Kotlin Multiplatform module. Its `jsMain` holds the shim: declarations whose signatures are copied verbatim from the upstream sources in this same tree, with bodies that push mutations and props through `external fun __fh_*` bridge calls. Its `jvmTest` compiles the guest to JavaScript, loads it into a real QuickJS instance via `quickjs-kt`, runs one composition frame, and asserts on the mutation stream that comes back. Nothing renders in the guest; the host that consumes the mutation stream lives in a separate application repository and is out of scope here.

**Tech Stack:** Kotlin Multiplatform, Kotlin/JS (IR, es2015), Compose Runtime (`:compose:runtime:runtime` from this build), `io.github.dokar3:quickjs-kt` (satisfied by `includeBuild("quickjs-kt")`), JUnit4 via `kotlin-test-junit`.

**Spec:** No separate spec document. The design was settled in conversation on 2026-08-17 and is captured in "Design Decisions" below; treat that section as the authority when the plan and a finding disagree.

## Global Constraints

- Repository root: `/Users/ilhom/AndroidStudioProjects/compose-multiplatform-core`. All implementation happens here. The application repository at `/Users/ilhom/AndroidStudioProjects/NativeCMPWeb` may be **read** for reference — it holds the working host renderer and the C bridge — but never modified by this plan.
- **Never add `-Xes-long-as-bigint`.** `target.set("es2015")` alone leaves `Long` as the two-Int emulation, which is correct. The BigInt flag converts every `Long` including `SnapshotIdSet`'s two 64-bit words (`SnapshotIdSet.kt:43-45`), whose `1L shl 63`, `.inv()` and `countTrailingZeroBits()` need wrap-around that arbitrary-precision BigInt does not have. Measured result: a 21-node screen never finishes composing and QuickJS sits at 100% CPU.
- Signatures are **copied from the upstream sources in this tree**, never invented or recalled. Cited paths are relative to the repo root; open the file and copy.
- The module's Gradle build file is `guest-shim/build.gradle.kts`, using `id("AndroidXComposePlugin")` and `id("kotlin-multiplatform")`. Do not add `AndroidXPlugin` — this module is not API-tracked or published.
- The version catalog in this fork is `gradle/libs-fork.versions.toml`, exposed as `libs`. The AOSP catalog is `aospLibs`. Use `libs`.
- `AndroidXComposePlugin` attaches the Compose compiler plugin to every compilation in the module, and that plugin refuses to run without compose-runtime on the classpath. `commonMain` must therefore depend on `project(":compose:runtime:runtime")` even where only JS needs it.
- Wire numbers (`NodeType`, `PropKey`, `PropValueType`) are a contract with a host that already exists. Copy the values exactly as this plan lists them; never renumber or reorder.

---

## Design Decisions

1. **Source compatibility is the goal.** Guest application code must compile with unmodified `androidx.compose.*` imports. That is why declarations live under real package names rather than a private one — the archived `remote-ui` module used `androidx.remoteui` and is explicitly *not* the model here.
2. **The guest emits nodes; the host renders real components.** No `Layout`, no measure policy, no `DrawScope` in the guest.
3. **Drawing is out of scope for this slice.** `DrawScope`, `ContentDrawScope`, `Canvas` and `drawBehind` are deferred by explicit decision. The agreed future design is: the guest records draw calls with their parameters into the batch, the host replays them on a real `Canvas`, and `DrawScope.size` reaches the guest by round-trip from host layout rather than by symbolic expressions.
4. **Proof is a JVM test, not a device.** `quickjs-kt` is wired into this build as a composite (`settings-fork.gradle`), so the compiled guest can be executed in real QuickJS from `jvmTest` and its mutation stream asserted. This replaces the build-a-bundle-and-look-at-the-screen loop.
5. **`Color` stays faithful to upstream** — `value class Color(val value: ULong)` (`compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/Color.kt:115`). The default Long representation makes this correct; colours are constructed per recomposition of a call site, not per frame.
6. **`Column`, `Row` and `Box` are `inline` upstream.** Preserve `inline`. It has been verified separately that Gradle dependency substitution links an inline `@Composable` correctly, so a downstream consumer can swap the real artifact for this module.
7. **Unsupported surface fails loudly.** Anything declared but not mapped to the wire throws `UnsupportedInGuestException` naming the call. A blank screen is worse than a crash with a name in it.

---

## File Structure

**New module `guest-shim/`**
- `guest-shim/build.gradle.kts` — KMP with `jvm()` and `js(IR)`, test wiring for the QuickJS harness
- `guest-shim/src/commonMain/kotlin/androidx/guestshim/Protocol.kt` — `NodeType`, `PropKey`, `PropValueType`, `UnsupportedInGuestException`
- `guest-shim/src/jsMain/kotlin/androidx/guestshim/Bridge.kt` — `external fun __fh_*`, `VNode`, `GuestApplier`, `emitNode`, `GuestRuntime`
- `guest-shim/src/jsMain/kotlin/androidx/compose/ui/Modifier.kt`
- `guest-shim/src/jsMain/kotlin/androidx/compose/ui/Alignment.kt`
- `guest-shim/src/jsMain/kotlin/androidx/compose/ui/unit/Dp.kt`
- `guest-shim/src/jsMain/kotlin/androidx/compose/ui/graphics/Color.kt`
- `guest-shim/src/jsMain/kotlin/androidx/compose/foundation/layout/Arrangement.kt`
- `guest-shim/src/jsMain/kotlin/androidx/compose/foundation/layout/Size.kt`
- `guest-shim/src/jsMain/kotlin/androidx/compose/foundation/Background.kt`
- `guest-shim/src/jsMain/kotlin/androidx/compose/foundation/layout/Column.kt`, `Row.kt`, `Box.kt`
- `guest-shim/src/jsMain/kotlin/androidx/guestshim/SliceScreen.kt` — the demo screen, written as ordinary Compose
- `guest-shim/src/jsMain/kotlin/androidx/guestshim/Main.kt` — guest entry point registering screens
- `guest-shim/src/jvmTest/kotlin/androidx/guestshim/GuestHarness.kt` — runs the guest in QuickJS, returns mutations
- `guest-shim/src/jvmTest/kotlin/androidx/guestshim/*Test.kt`

**Modified**
- `settings.gradle` — one `includeProject(":guest-shim")` line

---

### Task 1: Module skeleton and the QuickJS harness

The foundation. Until a guest can be compiled and run from a test, nothing else in this plan is verifiable, so this task ends with a test that actually executes JavaScript inside QuickJS.

**Files:**
- Create: `guest-shim/build.gradle.kts`
- Create: `guest-shim/src/commonMain/kotlin/androidx/guestshim/Protocol.kt`
- Create: `guest-shim/src/jsMain/kotlin/androidx/guestshim/Bridge.kt`
- Create: `guest-shim/src/jsMain/kotlin/androidx/guestshim/Main.kt`
- Create: `guest-shim/src/jvmTest/kotlin/androidx/guestshim/GuestHarness.kt`
- Create: `guest-shim/src/jvmTest/kotlin/androidx/guestshim/HarnessTest.kt`
- Modify: `settings.gradle`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `androidx.guestshim.NodeType` with `Root = 0`, `Text = 1`, `Column = 2`, `Row = 3`, `Box = 4`
  - `androidx.guestshim.PropValueType` with `Int = 0`, `Float = 1`, `String = 2`, `Bool = 3`, `Callback = 4`
  - `androidx.guestshim.UnsupportedInGuestException(call: String)`
  - `androidx.guestshim.VNode(id: Int, nodeTypeId: Int)`
  - `androidx.guestshim.GuestApplier(root: VNode)` — an `AbstractApplier<VNode>`
  - `@Composable fun emitNode(nodeTypeId: Int, content: @Composable () -> Unit = {}, update: Updater<VNode>.() -> Unit)`
  - `GuestRuntime.start(content: @Composable () -> Unit)` — installs the recomposer and runs one frame
  - `GuestHarness.runFrame(screen: String): Mutations` where `Mutations` exposes `mutations: List<Int>` and `strings: List<String>`

- [ ] **Step 1: Register the module**

In `settings.gradle`, add this line immediately after the `includeProject(":compose:ui:ui-backhandler")` line:

```groovy
includeProject(":guest-shim")
```

The fork's project filter always resolves to the `COMPOSE` subset and `includeProject` with no explicit filter list is always included, so no filter argument is needed.

- [ ] **Step 2: Write the build file**

`guest-shim/build.gradle.kts`. The js target, the compose-runtime dependency and the test system property are all modelled on the archived module at tag `remote-ui-archive` (`git show remote-ui-archive:remote-ui/build.gradle.kts`); read it if anything here is unclear.

```kotlin
plugins {
    id("AndroidXComposePlugin")
    id("kotlin-multiplatform")
}

kotlin {
    jvm()
    js(IR) {
        outputModuleName = "guest-shim"
        nodejs()
        binaries.executable()
        compilerOptions {
            // Do not add -Xes-long-as-bigint here. es2015 alone leaves Long as the two-Int
            // emulation, which is correct; the flag that changes it hangs the guest, because
            // SnapshotIdSet needs 64-bit wrap-around that arbitrary-precision BigInt lacks.
            target.set("es2015")
        }
    }

    sourceSets {
        // AndroidXComposePlugin attaches the Compose compiler plugin to every compilation in
        // this module, and that plugin refuses to run without compose-runtime on the classpath.
        commonMain.dependencies {
            implementation(project(":compose:runtime:runtime"))
        }

        commonTest.dependencies {
            implementation(libs.kotlinTest)
        }

        jsMain.dependencies {
            implementation(libs.kotlinCoroutinesCore)
        }

        jvmTest.dependencies {
            implementation(libs.kotlinTestJunit)
            // Substituted by includeBuild("quickjs-kt") in settings-fork.gradle.
            implementation("io.github.dokar3:quickjs-kt:1.0.11")
        }
    }
}

/** Where the compiled guest bundle lands: one .mjs per module, plus source maps. */
val guestBundleDir = layout.buildDirectory
    .dir("compileSync/js/main/productionExecutable/kotlin")

tasks.withType<Test>().configureEach {
    dependsOn("compileProductionExecutableKotlinJs")
    systemProperty("guestshim.guest.dir", guestBundleDir.get().asFile.absolutePath)
}
```

- [ ] **Step 3: Write the protocol**

`guest-shim/src/commonMain/kotlin/androidx/guestshim/Protocol.kt`. These numbers are a contract with an existing host — copy them exactly:

```kotlin
package androidx.guestshim

/** Component type, sent as an Int. The C bridge never interprets these. */
object NodeType {
    const val Root = 0
    const val Text = 1
    const val Column = 2
    const val Row = 3
    const val Box = 4
}

/** Prop keys, sent as Ints. */
object PropKey {
    const val PaddingTop = 10
    const val PaddingBottom = 11
    const val PaddingStart = 12
    const val PaddingEnd = 13
    const val Width = 14
    const val Height = 15
    const val BackgroundColor = 16
    const val FillMaxWidth = 20
    const val FillMaxHeight = 21
    const val HorizontalArrangement = 60
    const val VerticalArrangement = 61
    const val HorizontalAlignment = 62
    const val VerticalAlignment = 63
    const val ContentAlignment = 87
}

/** How the host should read a prop's Int bits. */
object PropValueType {
    const val Int = 0
    const val Float = 1
    const val String = 2
    const val Bool = 3
    const val Callback = 4
}

/** Thrown by declarations that exist for source compatibility but have no wire mapping. */
class UnsupportedInGuestException(call: String) :
    UnsupportedOperationException("$call is not supported in the guest runtime")
```

- [ ] **Step 4: Write the bridge, applier and runtime**

`guest-shim/src/jsMain/kotlin/androidx/guestshim/Bridge.kt`:

```kotlin
package androidx.guestshim

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.Updater
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

external fun __fh_mut(
    type: Int, nodeId: Int, parentId: Int,
    index: Int, fromIndex: Int, toIndex: Int, nodeTypeId: Int,
)

external fun __fh_prop(nodeId: Int, keyId: Int, valueType: Int, valueBits: Int)

external fun __fh_str(nodeId: Int, keyId: Int, value: String)

external fun __fh_commit()

internal const val MUT_CREATE = 0
internal const val MUT_INSERT = 1
internal const val MUT_REMOVE = 2
internal const val MUT_MOVE = 3
internal const val MUT_DELETE = 4

/** A guest-side node. Holds only what prop diffing needs; the host owns the real tree. */
class VNode(val id: Int, val nodeTypeId: Int) {
    internal val intCache = HashMap<Int, Int>()
    internal val floatCache = HashMap<Int, Float>()
    internal val strCache = HashMap<Int, String>()
    internal val children = mutableListOf<VNode>()
}

fun VNode.sendInt(keyId: Int, value: Int) {
    if (intCache[keyId] == value) return
    intCache[keyId] = value
    __fh_prop(id, keyId, PropValueType.Int, value)
}

fun VNode.sendFloat(keyId: Int, value: Float) {
    val bits = value.toBits()
    if (floatCache[keyId]?.toBits() == bits) return
    floatCache[keyId] = value
    __fh_prop(id, keyId, PropValueType.Float, bits)
}

fun VNode.sendBool(keyId: Int, value: Boolean) {
    val bits = if (value) 1 else 0
    if (intCache[keyId] == bits) return
    intCache[keyId] = bits
    __fh_prop(id, keyId, PropValueType.Bool, bits)
}

fun VNode.sendStr(keyId: Int, value: String) {
    if (strCache[keyId] == value) return
    strCache[keyId] = value
    __fh_str(id, keyId, value)
}

class GuestApplier(root: VNode) : AbstractApplier<VNode>(root) {
    private var nextId = 1

    override fun insertTopDown(index: Int, instance: VNode) {}

    override fun insertBottomUp(index: Int, instance: VNode) {
        current.children.add(index, instance)
        __fh_mut(MUT_INSERT, instance.id, current.id, index, -1, -1, -1)
    }

    override fun remove(index: Int, count: Int) {
        repeat(count) {
            val node = current.children.removeAt(index)
            __fh_mut(MUT_REMOVE, node.id, current.id, index, -1, -1, -1)
            // Ids are never reused and movable content is not supported, so a removed node
            // is gone for good. The host frees the subtree from its own child lists.
            __fh_mut(MUT_DELETE, node.id, -1, -1, -1, -1, -1)
        }
    }

    override fun move(from: Int, to: Int, count: Int) {
        val nodes = current.children.subList(from, from + count).toList()
        current.children.subList(from, from + count).clear()
        current.children.addAll(to, nodes)
        __fh_mut(MUT_MOVE, -1, current.id, -1, from, to, -1)
    }

    override fun onClear() {
        current.children.clear()
    }

    override fun onEndChanges() {
        __fh_commit()
    }

    fun createNode(nodeTypeId: Int): VNode {
        val node = VNode(id = nextId++, nodeTypeId = nodeTypeId)
        __fh_mut(MUT_CREATE, node.id, -1, -1, -1, -1, nodeTypeId)
        return node
    }
}

@Composable
fun emitNode(
    nodeTypeId: Int,
    content: @Composable () -> Unit = {},
    update: Updater<VNode>.() -> Unit,
) {
    val applier = currentComposer.applier as GuestApplier
    ComposeNode<VNode, GuestApplier>(
        factory = { applier.createNode(nodeTypeId) },
        update = update,
        content = content,
    )
}

/** Starts the Compose runtime in the guest and composes [content] once. */
object GuestRuntime {
    fun start(content: @Composable () -> Unit) {
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(clock + Job())
        val recomposer = Recomposer(scope.coroutineContext)
        val composition = Composition(GuestApplier(VNode(0, NodeType.Root)), recomposer)

        composition.setContent(content)
        Snapshot.registerGlobalWriteObserver { Snapshot.sendApplyNotifications() }
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
    }
}
```

- [ ] **Step 5: Write the guest entry point**

`guest-shim/src/jsMain/kotlin/androidx/guestshim/Main.kt`. The harness calls a global function by name, so screens are registered on `globalThis`:

```kotlin
package androidx.guestshim

import androidx.compose.runtime.Composable

/** Screens the harness can ask for by name. Task 5 adds the real one. */
private val screens: Map<String, @Composable () -> Unit> = mapOf(
    "empty" to {},
)

fun main() {
    val g: dynamic = js("globalThis")
    g.__runFrame = { name: String ->
        val screen = screens[name] ?: throw IllegalArgumentException("unknown screen: $name")
        GuestRuntime.start(screen)
    }
}
```

- [ ] **Step 6: Write the harness**

`guest-shim/src/jvmTest/kotlin/androidx/guestshim/GuestHarness.kt`. The archived harness at `git show remote-ui-archive:remote-ui/src/jvmTest/kotlin/androidx/remoteui/GuestHarness.kt` solves the same problem — read it for the module-loader shape, then write this one against the `__fh_*` names above:

```kotlin
package androidx.guestshim

import com.dokar.quickjs.ModuleContent
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.moduleLoader
import java.io.File
import kotlinx.coroutines.runBlocking

/** Everything one composition frame pushed across the bridge. */
data class Mutations(
    val mutations: List<Int>,
    val props: List<Int>,
    val strings: List<String>,
) {
    /** Mutation records are 7 ints wide: [type, nodeId, parentId, index, from, to, nodeTypeId]. */
    fun records(): List<List<Int>> = mutations.chunked(7)

    /** Prop records are 4 ints wide: [nodeId, keyId, valueType, valueBits]. */
    fun propRecords(): List<List<Int>> = props.chunked(4)
}

object GuestHarness {

    private val bundleDir: File
        get() = File(
            System.getProperty("guestshim.guest.dir")
                ?: error("guestshim.guest.dir is unset; the Gradle test task should set it"),
        )

    fun runFrame(screen: String): Mutations = runBlocking {
        val mutations = mutableListOf<Int>()
        val props = mutableListOf<Int>()
        val strings = mutableListOf<String>()

        QuickJs.create().use { js ->
            js.function("__fh_mut") { args ->
                repeat(7) { mutations.add((args[it] as Number).toInt()) }
            }
            js.function("__fh_prop") { args ->
                repeat(4) { props.add((args[it] as Number).toInt()) }
            }
            js.function("__fh_str") { args ->
                props.add((args[0] as Number).toInt())
                props.add((args[1] as Number).toInt())
                props.add(PropValueType.String)
                props.add(strings.size)
                strings.add(args[2] as String)
            }
            js.function("__fh_commit") { }

            js.moduleLoader { name ->
                val file = File(bundleDir, if (name.endsWith(".mjs")) name else "$name.mjs")
                require(file.exists()) { "guest module not found: ${file.absolutePath}" }
                ModuleContent(file.readText())
            }

            js.evaluate<Any?>(
                File(bundleDir, "guest-shim.mjs").readText(),
                filename = "guest-shim.mjs",
                asModule = true,
            )
            js.evaluate<Any?>("globalThis.__runFrame('$screen');")
        }

        Mutations(mutations, props, strings)
    }
}
```

If the `quickjs-kt` API differs from what is written here — the binding names in version 1.0.11 are the authority — adapt the calls and record what you changed in your report. The contract that matters is: four host functions registered, the bundle loaded module by module from `bundleDir`, `__runFrame` invoked, and the recorded ints returned.

- [ ] **Step 7: Write the failing test**

`guest-shim/src/jvmTest/kotlin/androidx/guestshim/HarnessTest.kt`:

```kotlin
package androidx.guestshim

import kotlin.test.Test
import kotlin.test.assertTrue

class HarnessTest {

    @Test
    fun `an empty screen composes without emitting nodes`() {
        val frame = GuestHarness.runFrame("empty")
        assertTrue(
            frame.records().none { it[0] == MUT_CREATE_TEST },
            "empty screen should create no nodes, got ${frame.records()}",
        )
    }

    private companion object {
        // Mirrors Bridge.kt's internal MUT_CREATE, which jvmTest cannot see from jsMain.
        const val MUT_CREATE_TEST = 0
    }
}
```

- [ ] **Step 8: Run it and make it pass**

Run:

```bash
cd /Users/ilhom/AndroidStudioProjects/compose-multiplatform-core
./gradlew :guest-shim:jvmTest --console=plain 2>&1 | tail -40
```

Expected on the first run: a failure that tells you something real — a missing module name, a QuickJS binding signature that differs from the sketch above, or a guest that throws. Work through those until the test passes. The one outcome that is **not** acceptable is making the test pass by not running JavaScript: if you find yourself stubbing the harness, stop and report `BLOCKED`.

- [ ] **Step 9: Commit**

```bash
git add settings.gradle guest-shim docs/superpowers/plans/2026-08-17-guest-shim-slice.md
git commit -m "feat(guest-shim): module skeleton, bridge and QuickJS test harness"
```

---

### Task 2: Value types — `Dp` and `Color`

**Files:**
- Create: `guest-shim/src/jsMain/kotlin/androidx/compose/ui/unit/Dp.kt`
- Create: `guest-shim/src/jsMain/kotlin/androidx/compose/ui/graphics/Color.kt`
- Test: `guest-shim/src/jvmTest/kotlin/androidx/guestshim/ValueTypeTest.kt`

**Interfaces:**
- Consumes: nothing from Task 1 except the module.
- Produces: `androidx.compose.ui.unit.Dp` (value class over `Float`), `val Int.dp`, `val Float.dp`, `val Double.dp`; `androidx.compose.ui.graphics.Color` (value class over `ULong`), `Color(Int)`, `Color(Long)`, `Color.toArgb()`, and the companion constants listed below.

These types live in `jsMain` and are only reachable from guest code, so they cannot be unit-tested from `jvmTest` directly. They are exercised through the guest in Task 5; this task's test asserts the packing rule that the host depends on, by way of a tiny guest screen.

- [ ] **Step 1: Implement `Dp`**

`guest-shim/src/jsMain/kotlin/androidx/compose/ui/unit/Dp.kt` — signature from `compose/ui/ui-unit/src/commonMain/kotlin/androidx/compose/ui/unit/Dp.kt:47`:

```kotlin
package androidx.compose.ui.unit

import kotlin.jvm.JvmInline

@JvmInline
value class Dp(val value: Float) : Comparable<Dp> {
    operator fun plus(other: Dp) = Dp(value + other.value)
    operator fun minus(other: Dp) = Dp(value - other.value)
    operator fun times(other: Float) = Dp(value * other)
    operator fun div(other: Float) = Dp(value / other)
    override fun compareTo(other: Dp): Int = value.compareTo(other.value)

    companion object {
        val Unspecified = Dp(Float.NaN)
        val Hairline = Dp(0f)
        val Infinity = Dp(Float.POSITIVE_INFINITY)
    }
}

val Int.dp: Dp get() = Dp(this.toFloat())
val Float.dp: Dp get() = Dp(this)
val Double.dp: Dp get() = Dp(this.toFloat())
```

- [ ] **Step 2: Implement `Color`**

`guest-shim/src/jsMain/kotlin/androidx/compose/ui/graphics/Color.kt`. Upstream is `value class Color(val value: ULong)` (`compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/Color.kt:115`) and packs the colour-space id into the low bits. Only sRGB is supported here, which is id 0, so ARGB occupies the top 32 bits exactly as upstream:

```kotlin
package androidx.compose.ui.graphics

import androidx.guestshim.UnsupportedInGuestException
import kotlin.jvm.JvmInline

@JvmInline
value class Color(val value: ULong) {
    companion object {
        val Black = Color(0xFF000000)
        val DarkGray = Color(0xFF444444)
        val Gray = Color(0xFF888888)
        val LightGray = Color(0xFFCCCCCC)
        val White = Color(0xFFFFFFFF)
        val Red = Color(0xFFFF0000)
        val Green = Color(0xFF00FF00)
        val Blue = Color(0xFF0000FF)
        val Yellow = Color(0xFFFFFF00)
        val Cyan = Color(0xFF00FFFF)
        val Magenta = Color(0xFFFF00FF)
        val Transparent = Color(0x00000000)
        val Unspecified = Color(0UL)
    }
}

/** Upstream shifts ARGB into the top 32 bits and leaves the sRGB colour-space id at 0. */
fun Color(color: Int): Color = Color(color.toULong().and(0xFFFFFFFFUL) shl 32)

fun Color(color: Long): Color = Color(color.toInt())

fun Color.toArgb(): Int = (value shr 32).toInt()

/** Declared for source compatibility; the guest has no colour-space machinery. */
fun Color.copy(
    alpha: Float = Float.NaN,
    red: Float = Float.NaN,
    green: Float = Float.NaN,
    blue: Float = Float.NaN,
): Color = throw UnsupportedInGuestException("Color.copy")
```

- [ ] **Step 3: Add a guest screen that exercises the packing**

Add to `guest-shim/src/jsMain/kotlin/androidx/guestshim/Main.kt`'s `screens` map an entry `"color"` whose body emits one Box node carrying a background colour. Since Task 3 has not built the modifiers yet, emit it directly:

```kotlin
    "color" to {
        emitNode(nodeTypeId = NodeType.Box) {
            set(Unit) { sendInt(PropKey.BackgroundColor, androidx.compose.ui.graphics.Color.Red.toArgb()) }
        }
    },
```

- [ ] **Step 4: Write the test**

`guest-shim/src/jvmTest/kotlin/androidx/guestshim/ValueTypeTest.kt`:

```kotlin
package androidx.guestshim

import kotlin.test.Test
import kotlin.test.assertEquals

class ValueTypeTest {

    @Test
    fun `Color Red reaches the host as opaque ARGB red`() {
        val frame = GuestHarness.runFrame("color")
        val backgroundProps = frame.propRecords().filter { it[1] == PropKey.BackgroundColor }
        assertEquals(1, backgroundProps.size, "expected one background prop, got $backgroundProps")
        assertEquals(0xFFFF0000.toInt(), backgroundProps.single()[3])
    }
}
```

- [ ] **Step 5: Run the test**

Run: `./gradlew :guest-shim:jvmTest --console=plain 2>&1 | tail -30`
Expected: PASS, both tests.

- [ ] **Step 6: Commit**

```bash
git add guest-shim
git commit -m "feat(guest-shim): Dp and Color with upstream packing"
```

---

### Task 3: `Modifier` and the layout modifiers

**Files:**
- Create: `guest-shim/src/jsMain/kotlin/androidx/compose/ui/Modifier.kt`
- Create: `guest-shim/src/jsMain/kotlin/androidx/compose/foundation/layout/Size.kt`
- Create: `guest-shim/src/jsMain/kotlin/androidx/compose/foundation/Background.kt`
- Modify: `guest-shim/src/jsMain/kotlin/androidx/guestshim/Main.kt`
- Test: `guest-shim/src/jvmTest/kotlin/androidx/guestshim/ModifierTest.kt`

**Interfaces:**
- Consumes: `VNode.sendFloat/sendBool/sendInt` (Task 1), `Dp`, `Color`, `toArgb` (Task 2).
- Produces: `androidx.compose.ui.Modifier` with `Element`, `CombinedModifier`, `foldIn`, `then`, and `Element.applyTo(node: VNode)`; `fun Modifier.applyAll(node: VNode)`; `Modifier.padding(all:)`, `padding(horizontal:, vertical:)`, `size(Dp)`, `width(Dp)`, `height(Dp)`, `fillMaxWidth(Float)`, `fillMaxHeight(Float)`, `fillMaxSize(Float)`, `background(Color)`.

- [ ] **Step 1: Implement `Modifier`**

`guest-shim/src/jsMain/kotlin/androidx/compose/ui/Modifier.kt` — shape from `compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/Modifier.kt`, reduced to what the guest needs. `applyTo` is our addition and is the only place a modifier touches the wire:

```kotlin
package androidx.compose.ui

import androidx.guestshim.VNode

interface Modifier {
    fun <R> foldIn(initial: R, operation: (R, Element) -> R): R

    fun then(other: Modifier): Modifier =
        if (other === Modifier) this else CombinedModifier(this, other)

    interface Element : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R =
            operation(initial, this)

        /** Writes this element's props to [node]. Called in declaration order. */
        fun applyTo(node: VNode) {}
    }

    companion object : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R = initial
        override fun then(other: Modifier): Modifier = other
    }
}

class CombinedModifier(
    private val outer: Modifier,
    private val inner: Modifier,
) : Modifier {
    override fun <R> foldIn(initial: R, operation: (R, Modifier.Element) -> R): R =
        inner.foldIn(outer.foldIn(initial, operation), operation)
}

/**
 * Applies every element to [node] in declaration order.
 *
 * Order is behaviour, not decoration: the host rebuilds the modifier chain from the order the
 * props arrive in, so `padding().background()` and `background().padding()` must produce
 * different prop orders.
 */
fun Modifier.applyAll(node: VNode) {
    foldIn(Unit) { _, element -> element.applyTo(node) }
}
```

- [ ] **Step 2: Implement the layout modifiers**

`guest-shim/src/jsMain/kotlin/androidx/compose/foundation/layout/Size.kt`:

```kotlin
package androidx.compose.foundation.layout

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.guestshim.PropKey
import androidx.guestshim.VNode
import androidx.guestshim.sendBool
import androidx.guestshim.sendFloat

private data class PaddingElement(
    val top: Dp, val bottom: Dp, val start: Dp, val end: Dp,
) : Modifier.Element {
    override fun applyTo(node: VNode) {
        node.sendFloat(PropKey.PaddingTop, top.value)
        node.sendFloat(PropKey.PaddingBottom, bottom.value)
        node.sendFloat(PropKey.PaddingStart, start.value)
        node.sendFloat(PropKey.PaddingEnd, end.value)
    }
}

private data class SizeElement(val width: Dp?, val height: Dp?) : Modifier.Element {
    override fun applyTo(node: VNode) {
        width?.let { node.sendFloat(PropKey.Width, it.value) }
        height?.let { node.sendFloat(PropKey.Height, it.value) }
    }
}

private data class FillElement(val width: Boolean, val height: Boolean) : Modifier.Element {
    override fun applyTo(node: VNode) {
        if (width) node.sendBool(PropKey.FillMaxWidth, true)
        if (height) node.sendBool(PropKey.FillMaxHeight, true)
    }
}

fun Modifier.padding(all: Dp): Modifier = then(PaddingElement(all, all, all, all))

fun Modifier.padding(horizontal: Dp = Dp(0f), vertical: Dp = Dp(0f)): Modifier =
    then(PaddingElement(vertical, vertical, horizontal, horizontal))

fun Modifier.padding(
    start: Dp = Dp(0f),
    top: Dp = Dp(0f),
    end: Dp = Dp(0f),
    bottom: Dp = Dp(0f),
): Modifier = then(PaddingElement(top, bottom, start, end))

fun Modifier.size(size: Dp): Modifier = then(SizeElement(size, size))

fun Modifier.size(width: Dp, height: Dp): Modifier = then(SizeElement(width, height))

fun Modifier.width(width: Dp): Modifier = then(SizeElement(width, null))

fun Modifier.height(height: Dp): Modifier = then(SizeElement(null, height))

fun Modifier.fillMaxWidth(fraction: Float = 1f): Modifier = then(FillElement(true, false))

fun Modifier.fillMaxHeight(fraction: Float = 1f): Modifier = then(FillElement(false, true))

fun Modifier.fillMaxSize(fraction: Float = 1f): Modifier = then(FillElement(true, true))
```

`guest-shim/src/jsMain/kotlin/androidx/compose/foundation/Background.kt`:

```kotlin
package androidx.compose.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.guestshim.PropKey
import androidx.guestshim.VNode
import androidx.guestshim.sendInt

private data class BackgroundElement(val color: Color) : Modifier.Element {
    override fun applyTo(node: VNode) {
        node.sendInt(PropKey.BackgroundColor, color.toArgb())
    }
}

fun Modifier.background(color: Color): Modifier = then(BackgroundElement(color))
```

- [ ] **Step 3: Add a guest screen that pins modifier order**

Replace the `"color"` entry in `Main.kt`'s `screens` map with:

```kotlin
    "modifierOrder" to {
        emitNode(nodeTypeId = NodeType.Box) {
            set(Unit) {
                androidx.compose.ui.Modifier
                    .padding(8.dp)
                    .background(androidx.compose.ui.graphics.Color.Red)
                    .fillMaxWidth()
                    .applyAll(this)
            }
        }
    },
```

with the imports `androidx.compose.foundation.background`, `androidx.compose.foundation.layout.fillMaxWidth`, `androidx.compose.foundation.layout.padding`, `androidx.compose.ui.applyAll` and `androidx.compose.ui.unit.dp` added to the file. Update `ValueTypeTest` to ask for `"modifierOrder"` instead of `"color"` and to assert the background prop within it.

- [ ] **Step 4: Write the test**

`guest-shim/src/jvmTest/kotlin/androidx/guestshim/ModifierTest.kt`:

```kotlin
package androidx.guestshim

import kotlin.test.Test
import kotlin.test.assertEquals

class ModifierTest {

    @Test
    fun `modifier elements reach the host in declaration order`() {
        val frame = GuestHarness.runFrame("modifierOrder")
        val keysInOrder = frame.propRecords().map { it[1] }.distinct()
        assertEquals(
            listOf(
                PropKey.PaddingTop, PropKey.PaddingBottom, PropKey.PaddingStart, PropKey.PaddingEnd,
                PropKey.BackgroundColor,
                PropKey.FillMaxWidth,
            ),
            keysInOrder,
        )
    }

    @Test
    fun `padding carries the dp value as float bits`() {
        val frame = GuestHarness.runFrame("modifierOrder")
        val top = frame.propRecords().single { it[1] == PropKey.PaddingTop }
        assertEquals(PropValueType.Float, top[2])
        assertEquals(8f, Float.fromBits(top[3]))
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :guest-shim:jvmTest --console=plain 2>&1 | tail -30`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add guest-shim
git commit -m "feat(guest-shim): Modifier chain and the layout modifiers"
```

---

### Task 4: `Column`, `Row` and `Box`

**Files:**
- Create: `guest-shim/src/jsMain/kotlin/androidx/compose/ui/Alignment.kt`
- Create: `guest-shim/src/jsMain/kotlin/androidx/compose/foundation/layout/Arrangement.kt`
- Create: `guest-shim/src/jsMain/kotlin/androidx/compose/foundation/layout/Column.kt`
- Create: `guest-shim/src/jsMain/kotlin/androidx/compose/foundation/layout/Row.kt`
- Create: `guest-shim/src/jsMain/kotlin/androidx/compose/foundation/layout/Box.kt`
- Test: `guest-shim/src/jvmTest/kotlin/androidx/guestshim/LayoutTest.kt`

**Interfaces:**
- Consumes: `emitNode`, `sendInt` (Task 1), `Modifier.applyAll` (Task 3).
- Produces: `Column`, `Row`, `Box` with upstream signatures emitting `NodeType.Column` (2), `NodeType.Row` (3), `NodeType.Box` (4); `ColumnScope`, `RowScope`, `BoxScope`; `Arrangement.Horizontal`/`Vertical` and `Alignment`/`Alignment.Horizontal`/`Alignment.Vertical`, each carrying `val wireValue: Int`.

The wire numbers below were read from the host renderer that already consumes them; the host is the authority and these are its values:

| Prop | Key | Mapping |
|---|---|---|
| `VerticalArrangement` | 61 | 0 Top · 1 Bottom · 2 Center · 3 SpaceBetween · 4 SpaceAround · 5 SpaceEvenly |
| `HorizontalArrangement` | 60 | 0 Start · 1 End · 2 Center · 3 SpaceBetween · 4 SpaceAround · 5 SpaceEvenly |
| `HorizontalAlignment` | 62 | 0 Start · 1 End · 2 CenterHorizontally |
| `VerticalAlignment` | 63 | 0 Top · 1 Bottom · 2 CenterVertically |
| `ContentAlignment` | 87 | 0 TopStart · 1 TopCenter · 2 TopEnd · 3 CenterStart · 4 Center · 5 CenterEnd · 6 BottomStart · 7 BottomCenter · 8 BottomEnd |

- [ ] **Step 1: Implement `Alignment`**

`guest-shim/src/jsMain/kotlin/androidx/compose/ui/Alignment.kt`. `wireValue` sits on `Alignment` itself because `Box`'s `contentAlignment` is the two-axis type:

```kotlin
package androidx.compose.ui

interface Alignment {
    val wireValue: Int

    interface Horizontal { val wireValue: Int }

    interface Vertical { val wireValue: Int }

    companion object {
        val TopStart: Alignment = BiasAlignment(0)
        val TopCenter: Alignment = BiasAlignment(1)
        val TopEnd: Alignment = BiasAlignment(2)
        val CenterStart: Alignment = BiasAlignment(3)
        val Center: Alignment = BiasAlignment(4)
        val CenterEnd: Alignment = BiasAlignment(5)
        val BottomStart: Alignment = BiasAlignment(6)
        val BottomCenter: Alignment = BiasAlignment(7)
        val BottomEnd: Alignment = BiasAlignment(8)

        val Start: Horizontal = HorizontalAlignment(0)
        val End: Horizontal = HorizontalAlignment(1)
        val CenterHorizontally: Horizontal = HorizontalAlignment(2)

        val Top: Vertical = VerticalAlignment(0)
        val Bottom: Vertical = VerticalAlignment(1)
        val CenterVertically: Vertical = VerticalAlignment(2)
    }
}

data class BiasAlignment(override val wireValue: Int) : Alignment

private data class HorizontalAlignment(override val wireValue: Int) : Alignment.Horizontal

private data class VerticalAlignment(override val wireValue: Int) : Alignment.Vertical
```

- [ ] **Step 2: Implement `Arrangement`**

`guest-shim/src/jsMain/kotlin/androidx/compose/foundation/layout/Arrangement.kt`:

```kotlin
package androidx.compose.foundation.layout

object Arrangement {
    interface Horizontal { val wireValue: Int }

    interface Vertical { val wireValue: Int }

    interface HorizontalOrVertical : Horizontal, Vertical

    private data class Horiz(override val wireValue: Int) : Horizontal

    private data class Vert(override val wireValue: Int) : Vertical

    private data class Both(override val wireValue: Int) : HorizontalOrVertical

    val Top: Vertical = Vert(0)
    val Bottom: Vertical = Vert(1)
    val Start: Horizontal = Horiz(0)
    val End: Horizontal = Horiz(1)
    val Center: HorizontalOrVertical = Both(2)
    val SpaceBetween: HorizontalOrVertical = Both(3)
    val SpaceAround: HorizontalOrVertical = Both(4)
    val SpaceEvenly: HorizontalOrVertical = Both(5)
}
```

- [ ] **Step 3: Implement `Column`**

`guest-shim/src/jsMain/kotlin/androidx/compose/foundation/layout/Column.kt`. Signature verbatim from `compose/foundation/foundation-layout/src/commonMain/kotlin/androidx/compose/foundation/layout/Column.kt` — keep `inline`:

```kotlin
package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.applyAll
import androidx.guestshim.NodeType
import androidx.guestshim.PropKey
import androidx.guestshim.emitNode
import androidx.guestshim.sendInt

interface ColumnScope

@PublishedApi
internal object ColumnScopeInstance : ColumnScope

@Composable
inline fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    emitNode(
        nodeTypeId = NodeType.Column,
        content = { ColumnScopeInstance.content() },
    ) {
        set(modifier) { value -> value.applyAll(this) }
        set(verticalArrangement) { value ->
            sendInt(PropKey.VerticalArrangement, value.wireValue)
        }
        set(horizontalAlignment) { value ->
            sendInt(PropKey.HorizontalAlignment, value.wireValue)
        }
    }
}
```

`ColumnScopeInstance` is `@PublishedApi internal` because a `public inline` function touching a plain `internal` declaration is an error under newer language versions; upstream has the same shape for the same reason.

If `set(modifier) { value -> value.applyAll(this) }` fails to compile because `this` resolves to the `Updater` rather than the node, bind the receiver explicitly: `set(modifier) { value -> value.applyAll(this@set) }`.

- [ ] **Step 4: Implement `Row` and `Box`**

`Row.kt`, signature verbatim from upstream `Row.kt`:

```kotlin
package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.applyAll
import androidx.guestshim.NodeType
import androidx.guestshim.PropKey
import androidx.guestshim.emitNode
import androidx.guestshim.sendInt

interface RowScope

@PublishedApi
internal object RowScopeInstance : RowScope

@Composable
inline fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
) {
    emitNode(
        nodeTypeId = NodeType.Row,
        content = { RowScopeInstance.content() },
    ) {
        set(modifier) { value -> value.applyAll(this) }
        set(horizontalArrangement) { value ->
            sendInt(PropKey.HorizontalArrangement, value.wireValue)
        }
        set(verticalAlignment) { value ->
            sendInt(PropKey.VerticalAlignment, value.wireValue)
        }
    }
}
```

`Box.kt`, signatures verbatim from upstream `Box.kt:65` and `Box.kt:233` — both overloads are needed, because guest code writes `Box(modifier = …)` with no content:

```kotlin
package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.applyAll
import androidx.guestshim.NodeType
import androidx.guestshim.PropKey
import androidx.guestshim.emitNode
import androidx.guestshim.sendInt

interface BoxScope

@PublishedApi
internal object BoxScopeInstance : BoxScope

@Composable
inline fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    emitNode(
        nodeTypeId = NodeType.Box,
        content = { BoxScopeInstance.content() },
    ) {
        set(modifier) { value -> value.applyAll(this) }
        set(contentAlignment) { value -> sendInt(PropKey.ContentAlignment, value.wireValue) }
    }
}

@Composable
fun Box(modifier: Modifier) {
    emitNode(nodeTypeId = NodeType.Box) {
        set(modifier) { value -> value.applyAll(this) }
    }
}
```

- [ ] **Step 5: Write the test**

Add a `"layout"` screen to `Main.kt` — a `Column` containing a `Row` containing two `Box`es — then `guest-shim/src/jvmTest/kotlin/androidx/guestshim/LayoutTest.kt`:

```kotlin
package androidx.guestshim

import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutTest {

    @Test
    fun `nested layout emits the expected node types`() {
        val frame = GuestHarness.runFrame("layout")
        val created = frame.records().filter { it[0] == 0 }.map { it[6] }
        assertEquals(listOf(NodeType.Column, NodeType.Row, NodeType.Box, NodeType.Box), created)
    }

    @Test
    fun `arrangement and alignment reach the host as the numbers it expects`() {
        val frame = GuestHarness.runFrame("layout")
        val vertical = frame.propRecords().single { it[1] == PropKey.VerticalArrangement }
        assertEquals(2, vertical[3], "Arrangement.Center should be 2")
        val horizontal = frame.propRecords().single { it[1] == PropKey.HorizontalArrangement }
        assertEquals(3, horizontal[3], "Arrangement.SpaceBetween should be 3")
    }
}
```

Write the `"layout"` screen so those assertions hold: the `Column` uses `verticalArrangement = Arrangement.Center` and the `Row` uses `horizontalArrangement = Arrangement.SpaceBetween`.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :guest-shim:jvmTest --console=plain 2>&1 | tail -30`
Expected: PASS, all tests.

- [ ] **Step 7: Commit**

```bash
git add guest-shim
git commit -m "feat(guest-shim): Column, Row and Box emitting host nodes"
```

---

### Task 5: Prove source compatibility against real Compose

The point of the whole exercise: one file of ordinary Compose source that compiles **both** against this shim (for JS) and against the real `foundation-layout` (for JVM). If it compiles both ways, source compatibility is proven by the compiler rather than by inspection.

**Files:**
- Create: `guest-shim/src/sliceScreen/kotlin/androidx/guestshim/SliceScreen.kt` — the shared source
- Modify: `guest-shim/build.gradle.kts` — add the directory to both `jsMain` and `jvmMain`, and give `jvmMain` the real Compose dependencies
- Modify: `guest-shim/src/jsMain/kotlin/androidx/guestshim/Main.kt` — register the screen
- Test: `guest-shim/src/jvmTest/kotlin/androidx/guestshim/SliceTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 1-4.
- Produces: `androidx.guestshim.SliceScreen()` — a `@Composable` compiled twice from one source.

- [ ] **Step 1: Write the shared screen**

`guest-shim/src/sliceScreen/kotlin/androidx/guestshim/SliceScreen.kt`. Every import is a real Compose import; nothing here knows a shim exists. No text, so `TextStyle` and `sp` stay out of the slice:

```kotlin
package androidx.guestshim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SliceScreen() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(modifier = Modifier.size(60.dp).background(Color.Red))
            Box(modifier = Modifier.size(60.dp).padding(8.dp).background(Color.Green))
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(40.dp).background(Color.White))
        }
    }
}
```

- [ ] **Step 2: Compile it twice**

In `guest-shim/build.gradle.kts`, inside `sourceSets`, add the shared directory to both targets and give the JVM side the real Compose:

```kotlin
        jsMain {
            kotlin.srcDir("src/sliceScreen/kotlin")
        }

        jvmMain {
            kotlin.srcDir("src/sliceScreen/kotlin")
            dependencies {
                implementation(project(":compose:foundation:foundation-layout"))
                implementation(project(":compose:foundation:foundation"))
                implementation(project(":compose:ui:ui"))
                implementation(project(":compose:ui:ui-graphics"))
                implementation(project(":compose:ui:ui-unit"))
            }
        }
```

- [ ] **Step 3: Compile the JVM side and fix what it reveals**

Run:

```bash
./gradlew :guest-shim:compileKotlinJvm --console=plain 2>&1 | tail -40
```

This is the real test of the shim's fidelity. Every error here is a place where our declaration and upstream's disagree — a missing overload, a wrong default, a parameter name that differs. Fix the **shim**, never the shared screen: the screen is what an application would write, and bending it to fit the shim would defeat the exercise. Record every mismatch you fix in your report; that list is the most valuable output of this task.

- [ ] **Step 4: Compile the JS side**

Run:

```bash
./gradlew :guest-shim:compileKotlinJs --console=plain 2>&1 | tail -40
```

Expected: BUILD SUCCESSFUL. If a fix from Step 3 broke the JS side, the two declarations have genuinely diverged — resolve it by matching upstream, since upstream is the contract.

- [ ] **Step 5: Register and assert the screen**

Add `"slice" to { SliceScreen() }` to `Main.kt`'s `screens` map, then `guest-shim/src/jvmTest/kotlin/androidx/guestshim/SliceTest.kt`:

```kotlin
package androidx.guestshim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SliceTest {

    @Test
    fun `the slice screen emits the tree the host will render`() {
        val frame = GuestHarness.runFrame("slice")
        val created = frame.records().filter { it[0] == 0 }.map { it[6] }
        assertEquals(
            listOf(
                NodeType.Column,
                NodeType.Row, NodeType.Box, NodeType.Box,
                NodeType.Box, NodeType.Box,
            ),
            created,
        )
    }

    @Test
    fun `colours survive the trip as opaque ARGB`() {
        val frame = GuestHarness.runFrame("slice")
        val backgrounds = frame.propRecords()
            .filter { it[1] == PropKey.BackgroundColor }
            .map { it[3] }
        assertTrue(0xFFFF0000.toInt() in backgrounds, "red missing, got $backgrounds")
        assertTrue(0xFF000000.toInt() in backgrounds, "black missing, got $backgrounds")
        assertTrue(0xFFFFFFFF.toInt() in backgrounds, "white missing, got $backgrounds")
    }
}
```

- [ ] **Step 6: Run the full suite**

Run: `./gradlew :guest-shim:jvmTest --console=plain 2>&1 | tail -30`
Expected: PASS, every test.

- [ ] **Step 7: Commit**

```bash
git add guest-shim
git commit -m "feat(guest-shim): prove one Compose source compiles against both shim and upstream"
```

---

## Self-Review Notes

- **Coverage:** Design Decisions 1-7 map to tasks — 1 to Task 5, 2 to Task 1, 3 excluded by scope, 4 to Task 1, 5 to Task 2, 6 to Task 4, 7 to Task 2's `Color.copy`.
- **Known soft spot:** Task 1 Step 6 sketches the `quickjs-kt` binding API from the archived harness rather than from the 1.0.11 source. The task text says so and tells the implementer to adapt and report — this is the highest-risk step in the plan.
- **Known soft spot:** Task 4 Step 3 flags the `Updater.set` receiver ambiguity with its fallback inline.
- **Deliberately not covered:** `ColumnScope.weight`, `align` scope modifiers, `Text`, events and everything in Design Decision 3. `weight` and `align` are the natural next slice, since the existing host already renders both.
