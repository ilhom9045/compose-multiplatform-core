# guest-shim

The Compose API surface as the guest sees it. Compiled to JS, executed inside QuickJS on the
host. Composition runs in the guest; nothing is drawn there. Every visible effect is produced by
the host, which owns the real Compose UI.

## Writing a guest

An app is a `main` and one call:

```kotlin
fun main() = setContent {
    Column(Modifier.padding(8.dp).background(Color.Red).fillMaxWidth()) {
        BasicText("hi")
    }
}
```

`setContent` wires up everything the host expects on the other side of the bridge — the frame and
event entry points it calls back through — and starts composition as it runs, because the host
evaluates the bundle and calls nothing afterwards. Nothing about frames, globals or node ids
appears in an app.

It composes a single root and refuses a second call: node ids are handed out per composition, so
another one would hand the host ids it has already seen.

`Main.kt` in this module is not that shape — it keeps a map of screens and two globals so the test
harness can drive each in turn. That is scaffolding for the shim's own tests, not the API.

## The copy rule

**Copy upstream verbatim. Change behaviour in exactly two places: `Modifier` and `@Composable`
functions.**

Everything else — value classes, math, operators, `companion object` constants, extension
properties — is transcribed from the upstream source unchanged, body included. Those are pure
computation over primitives. They have no host to talk to and nothing to send, so a rewritten
body could only differ from upstream by being wrong.

The two exceptions are the boundary itself:

| Kind | Upstream body | Guest body |
|---|---|---|
| value types (`Dp`, `Color`, `Offset`, …) | pure math | **identical** |
| `Modifier` factories (`padding`, `background`, …) | build a `ModifierNodeElement` | record the element into the current node's prop batch |
| `@Composable` functions (`Column`, `Text`, …) | emit layout/draw nodes | emit a host node id + its props |

A `Modifier` chain and a composable call are the only things whose *meaning* is "make the host do
something". They collect, and the batch crosses to native once per commit, where the host applies
the real upstream `Modifier` / composable.

## What crosses the wire

Only the packed primitive, never the object. `Color` goes over as its `value`; `Dp` as its
`value`; `DpOffset` as its `packedValue`. Everything else on those types — `Color.red`,
`Color.luminance()`, `Color.copy()`, `Dp.coerceIn` — exists so guest code can *compute* a value
before sending it. Those calls never reach the host and never appear on the wire.

This is why the verbatim body matters: `Color.Red.toArgb()` must produce `0xFFFF0000` in the guest
by running upstream's own bit-twiddling, not by our re-derivation of it.

## Why copy instead of depending on the module

`Dp` lives in `:compose:ui:ui-unit`, which is skiko-free and could be depended on directly.
`Color` lives in `:compose:ui:ui-graphics`, whose `nonJvmMain` does `dependsOn(skikoMain)` →
`api(libs.skiko)` — a guest that depends on it drags a graphics engine it will never call.

Rather than deciding per module — depend here, copy there, re-litigate on every new type — the
rule is uniform: **copy**. One rule to apply, no archaeology per file, and the guest's dependency
graph stays exactly as small as what it uses.

## Layout

Copies go in **`commonMain`**, mirroring the upstream package path, so the source of any file is
its own address:

```
guest-shim/src/commonMain/kotlin/androidx/compose/ui/unit/Dp.kt
        ← compose/ui/ui-unit/src/commonMain/kotlin/androidx/compose/ui/unit/Dp.kt
```

`commonMain` is not a preference, it is required. Upstream's files are common sources and use
`@JvmInline`, `@JvmField` and `@JvmOverloads`, which are `kotlin.jvm` declarations carrying
`@OptionalExpectation` — the compiler rejects those outside a common source set. Putting the
copies in `jsMain` fails with *"Declaration annotated with '@OptionalExpectation' can only be used
in common module sources"* on every value class, and the only way to keep them in a platform
source set would be to edit the annotations off 33 declarations. Common-to-common keeps them
verbatim.

Copy in batches, one upstream file at a time, with its transitive declarations. A partial file is
worse than no file: the missing half fails at link time in app code, far from here.

## Copied so far

Deviations from verbatim are marked with a comment at the point of change, so a later comparison
against upstream can tell an intentional edit from drift.

67 files, copied whole-module from `commonMain`: **ui-geometry**, **ui-unit**, **ui-util**, and
the value-type half of **ui-graphics** (`Color`, `colorspace/`, `Matrix`, `ColorMatrix`,
`Float16`, `Bezier`, `PathSegment`, the enums).

Two non-Compose dependencies come with them, used by the copies and not by the guest itself:
`androidx.annotation` (erased, emits no code) and `androidx.collection`, which
`colorspace/Connector.kt` uses to cache connectors. `jsMain` pins the JetBrains repackaging of
the latter, the same way ui-graphics does for its own nonJvm sources.

### Flattened `expect`/`actual`

An `expect` in `commonMain` needs an actual in *every* target — here both `jsMain` (the guest) and
`jvmMain` (the test harness) — for declarations the guest never calls. Five files had pure
actuals, inlined verbatim at the point of declaration and marked with a comment:

| File | Actual taken from |
|---|---|
| `ui/util/InlineClassHelper.kt` | `InlineClassHelper.nonJvm.kt` |
| `ui/util/Trace.kt` | `Trace.web.kt` |
| `ui/unit/FontScaling.kt` | `FontScacling.nonAndroid.kt` (`typealias FontScaling = FontScalingLinear`) |
| `ui/{unit,graphics}/internal/JvmDefaultWithCompatibility.kt` | the matching `.nonJvm.kt` |

### Not copied: the drawing surface

`Canvas`, `Path`, `Paint`, `ImageBitmap`, `Shader`, `ColorFilter`, `PathEffect`, `RenderEffect`,
`BlendMode`, `TileMode`, `PathIterator`, `PathMeasure`, `GraphicsLayer`, `Blur` — 14 files whose
js actuals wrap skiko — plus everything that transitively needs them: `Brush`, `drawscope/`,
`painter/`, `vector/` (except `PathNode`/`PathBuilder`), `layer/`, `shadow/`. All inside
`graphics/`; no value type was touched by the cut.

These are the one place the copy rule does not reach. Their bodies are not transcription — a
guest `Canvas` would have to *record* each call onto the wire, which is the boundary work this
document reserves for `Modifier` and `@Composable`. Copying 3100 lines of declarations that can
only throw would buy nothing until someone needs `Modifier.drawBehind`.

### Shapes

`Shape`, `Outline`, `CornerSize`, `CornerBasedShape` and `RoundedCornerShape` came back later and
are copies like everything else — they turned out to need only geometry and units, which the guest
already has. Two deviations, both marked in place:

- `Outline.Generic` wraps a `Path`, and the rest of `Outline.kt` draws through `DrawScope`,
  `Canvas` and `Path`. `Rectangle` and `Rounded` are pure geometry and are copied unchanged.
- `CutCornerShape` is built entirely from a `Path`, and the wire has no cut-corner type.

A shape never crosses as an `Outline`: `createOutline` needs the size and density that only the
host has at layout, so the guest sends what the shape *is* — `ClipShapeType` (0 rectangle,
1 rounded, 2 circle) plus four corner radii in dp — and the host rebuilds the real one.

`CornerSize` only exposes `toPx(shapeSize, density)` and its implementations are private, so the
radius is read by asking: at density 1 a dp corner answers its own number. Asking twice with
different sizes is what separates a dp corner from a percentage one, and a percentage corner is
refused — what it would be a percentage *of* is the host's to know. `CircleShape` is
`RoundedCornerShape(50)`, a percentage shape, so it is recognised by value and sent as its own
type instead.

## The boundary, as built

`Modifier.kt`, `Alignment.kt` and `Arrangement.kt` are copies like everything else — a modifier
chain is a linked list and an alignment is a singleton, both pure data. Only `Modifier.Node` was
cut from `Modifier.kt`: it is the hook into the host's layout/draw node system (`DelegatableNode`,
`NodeCoordinator`, `NodeKind`), and app code never names it.

What is ours is the two things the rule reserves:

| | Implemented | Crosses as |
|---|---|---|
| Modifier factories | `background`, `clip`, `clickable`, `padding` ×3, `size` ×2, `width`, `height`, `fillMaxWidth`, `fillMaxHeight`, `fillMaxSize` | props on the node they decorate |
| Composables | `Box`, `Column`, `Row`, `Text`, `BasicText` | one host node each, parameters as props |

A chain is collected into a `ShimProps` (`Modifier.toProps()`) and written whole. **Every field is
written unconditionally, including fields still at their default.** `Updater.set` fires when a
value returns to its default just as it does when it leaves it, so a default is how a *removed*
modifier gets undone; guarding the write with `if (value != default)` is exactly what makes a
removed modifier impossible to reset — the host keeps whatever it was last told. `sendInt` and
`sendFloat` already drop writes that did not change, so an untouched prop costs nothing.

**A changed chain travels whole.** The host clears a node's modifier order on the first modifier
prop of a batch and rebuilds it from that batch alone, so a batch carrying only the colour that
changed would drop padding, clip and fill out of the chain — their values still in the host's map,
with nothing walking them. Modifier props are therefore written without the usual unchanged-value
dedup. Skipping happens at a coarser grain instead: `sendProps` is called from
`Updater.set(modifier)`, which fires only when the chain itself changed, so an idle node still
sends nothing.

Padding accumulates across a chain (`.padding(8.dp).padding(4.dp)` is 12dp), matching how nested
upstream padding modifiers compose. Everything else is last-wins.

Arrangements and alignments cross as small ints (`WireId`): the host holds the real singletons.
`Arrangement.spacedBy` and custom `Alignment` instances carry a value instead of being one of the
known singletons and have no id, so they throw rather than silently arriving as `Top`.

**Not covered, and each fails loudly rather than quietly:** `BoxScope.align`/`matchParentSize`,
`ColumnScope`/`RowScope` `align`/`weight` (per-child layout the wire has no prop for),
`Box(propagateMinConstraints = true)`, `background(shape = …)` (the shape parameter, not the type —
the wire carries one shape per node, and `clip` already has it), a percentage `CornerSize`, and
`BasicText`'s text-styling parameters (need ui-text).

### Frames

`GuestHarness.runFrames(screen, vararg flags)` composes the screen and then drives one more frame
per flag, returning one `Mutations` per commit. `runFrame` is the first of them.

It exists because a single frame cannot tell a prop that was never set from one that was set and
then taken away — at mount both are a default being written, which is exactly why the guarded-write
bug survives review. `ResetTest` toggles a modifier on and back off and asserts all three frames.

Two behaviours fall out of it, both asserted:

- **An idle frame is silent.** A frame with no state change produces no commit at all, not an empty
  one — the recomposer never reaches `onEndChanges`. Nothing reaches the host when nothing moved.
- **An unchanged prop is not resent.** Writing the same value again recomposes nothing, so a
  screen that keeps writing defaults costs one batch, not one per frame.

## The host

The other half of the wire lives in **NativeCMPWeb** (`/Users/ilhom/AndroidStudioProjects/
NativeCMPWeb`): `runtime/src/nativeMain/.../NodeRenderer.kt` turns node types and props into real
Compose components, `NativeRenderTree.kt` applies the mutations, and `runtime/src/commonCpp/`
buffers a batch and crosses to Kotlin once per commit.

`Protocol.kt` here is a **verbatim copy of that project's `protocol/NodeType.kt`** — the whole
table, not the subset this guest uses. The host owns those numbers; nothing may be renumbered on
this side alone. The subset version drifted immediately: it had `Text = 1`, which is the host's
`FontSize`, so a string prop would have arrived as a font size with nothing failing anywhere.

The external functions match exactly — `__fh_mut` / `__fh_prop` / `__fh_str` / `__fh_commit`, same
argument order, same placement of `-1` in each of the five mutation kinds, same `MutationType` and
`PropValueType` values. The host additionally offers `__fh_long` and `__fh_double`, which this
guest does not use yet.

Two disagreements were found by reading the host and both are fixed:

- **`width`/`height` when unset.** The guest writes every prop every time, so "no size modifier"
  arrives as `Float.NaN`, while the host's convention was *key present means apply* — it would
  have called `Modifier.width(Dp.Unspecified)`. NaN is Compose's own encoding for unspecified, so
  `NodeRenderer` now skips it. The reset stays representable; the host stops acting on it.
- **`fillMaxWidth(fraction)`.** The host reads that key as a flag, not a fraction, so a partial
  fill would have silently become a full one. The guest now throws for any fraction but `1f`.
- **Modifier order was being thrown away.** The host rebuilds a chain by walking the order props
  arrived in — that is what its `modifierOrder` list is for — and the guest was emitting a fixed
  order, which makes `padding().background()` and `background().padding()` identical on the wire
  though they are different pictures. `ShimProps` now records the order the chain first touched
  each group, `sendProps` emits those first and the untouched groups after, and both sides assert
  it.

Both halves are checked against the same numbers. `ModifierTest` here asserts what the guest puts
on the wire for `Column(Modifier.padding(8.dp).background(Color.Red).fillMaxWidth())`;
`GuestWireTest` over in NativeCMPWeb's `runtime/src/jvmTest` replays exactly those records through
`NativeRenderTree` and asserts the `Modifier` that comes out is the one the app wrote. That runs on
the JVM — `jvmMain dependsOn(nativeMain)` — so it needs no bundle, no signature and no simulator.

### Events

`Modifier.clickable` is the first prop that travels both ways. The lambda never crosses: the guest
sends `OnClick` with value type `Callback` and an empty payload, meaning *a handler exists*; the
host registers a stub and calls back through `__runtime_onEvent(nodeId, keyId)`. The guest finds
the handler in the node the applier created under that id and runs it, and whatever state it
touched shows up on the next frame the host asks for.

This one prop breaks the "write every group every time" rule, because the host's encoding has no
value meaning *no handler*. A removed `clickable` — or `enabled = false` — travels as the prop
being absent from the batch, which the order rebuild above turns into a node without it. That is
also why `OnClick` is not in `AllGroups`.

An event naming a node the guest no longer has is ignored, not fatal: a click reported for a node
removed in the same frame is a race the host cannot avoid.

### Text

`material3.Text` carries `color` and `fontSize` because the wire has flat keys for them —
`BasicText`'s styling goes through a `TextStyle`, which needs ui-text. Size crosses as a float in
sp, and `TextUnit.Unspecified` crosses as `NaN`, which is the host's own encoding for it; so size
is written every frame and a size returning to the default resets properly.

**Colour cannot reset.** The host reads an *absent* `Color` key as `Color.Unspecified`, and every
Int is a real colour — 0 is transparent black, not "no colour". So the guest writes the key only
when a colour is set, which is the guarded write that `Updater.set` normally punishes: a `Text`
that has been given a colour cannot go back to the theme default. It is kept deliberately, because
there is no value to send instead. Fixing it takes a sentinel agreed on both sides; the same limit
exists in the host's own shim today.

### Getting it on screen

The host loads a signed `.ncwb` — a zip of QuickJS bytecode plus manifest hashes and an RSA
signature — so the guest reaches pixels through their bundle pipeline:

```
./gradlew :composeApp:run -PguestJsDir=<abs path to guest-shim's productionExecutable/kotlin>
```

`guestJsDir` (added to `runtime/build.gradle.kts`) points the bundle build at this guest's output
instead of `runtime/src/jsMain`. Two things had to change on the host for `.mjs` to get through:

- `runtime/tools/compile_js.c` compiled everything as a script. It now compiles `.mjs` as a module,
  names the module after the file's basename so `import './name.mjs'` resolves to it, and installs
  a module loader — QuickJS resolves a module's imports while compiling it, so the dependencies
  have to be readable from the same directory.
- `runtime/src/commonCpp/quickjs_runtime.cpp` read bytecode and evaluated it directly, which is
  right for a script but not for a module: module bytecode arrives with its imports unbound, so it
  now calls `JS_ResolveModule` first.

No module loader is needed in the engine itself, because the bundle carries `load-order.txt` and
`JS_ReadObject` registers each module under the name in its bytecode — by the time anything is
resolved, its dependencies are already in the module cache. A loader is what would let that file
go away, and is what dynamic `import()` would need.

The guest composes its own root as it loads; nothing calls into it afterwards. `globalThis.__screen`
picks which screen to mount if the embedder sets it before loading, which is how the test harness
chooses one.

**Rendered:** `Column(Modifier.padding(8.dp).background(Color.Red).fillMaxWidth())` with a
`BasicText("hi")` inside draws as a red full-width band with the text in it, in a desktop window,
composed in QuickJS and rendered by the host's real Compose components.

## Is it actually a replacement?

The point of copying upstream is that app code written against real Compose compiles against the
shim unchanged. That is a claim, and until it is compiled both ways it is only a claim — every
screen written while looking at the shim will pass, because it was written to fit.

`guest-shim/sample/` is ordinary Compose that knows nothing about the guest, compiled from one
place by two compilations:

```
./gradlew :guest-shim:compileProductionExecutableKotlinJs   # against the shim
./gradlew :guest-shim-check:compileKotlinDesktop            # against the real Compose in this tree
```

`:guest-shim-check` has no sources of its own; it adds `../guest-shim/sample` as a source directory
and depends on `:compose:foundation:foundation`, `:compose:material3:material3` and the rest as
*project* references. The oracle is the very upstream the shim was copied from, in the state this
tree has it, not some published artifact that may have drifted.

It catches what it is meant to. Writing `background(Color.Red, RoundedCornerShape(4.dp))` — a shape
parameter the shim dropped — compiles against real Compose and fails against the shim with *"Too
many arguments for fun Modifier.background(color: Color)"*. A signature that drifts stops being a
screen rendering slightly wrong and becomes a build failure.

**It only covers what the sample uses.** Growing the sample is how the covered surface grows, and
each addition is a small piece of work: `Text(fontWeight = …)`, `padding(PaddingValues(…))`,
`Modifier.alpha`/`border`/`offset` — all of which the host already has prop keys for — would each
turn the js side red today. That list is the backlog, and now it is a compiler's list rather than
one somebody has to remember.

Also unchecked by this: the sample is the demo, so `Main.kt`'s `"layout"` screen is `sample.App()`.
The other screens there stay hand-written because they exist to poke at edges the API does not
expose — a percentage corner, a modifier that comes and goes.

## Keeping up with JetBrains

Upstream lives in this same tree (this is the JetBrains fork), so a merge from `jb-main` updates
`compose/ui/**` and leaves `guest-shim/**` untouched. Git will not conflict and will not warn.
The signal comes from one file per module instead:

```
git diff jb-main --stat -- '**/api/*.klib.api'
```

`*.klib.api` changes **only** when the API changes — a rewritten body never touches it, and
JetBrains' own CI forces it to stay accurate. So:

- dump unchanged → implementation-only change upstream → **not ours**, ignore it;
- dump changed → read the diff, mirror the added/changed declarations into the copy.

That is the entire upkeep procedure. No reading 400-line source diffs, no auditing files that
did not change.

## Unsupported calls

A declaration that exists upstream but cannot work in the guest is still declared, with a body of
`throw UnsupportedInGuestException("<name>")` (see `Protocol.kt`). The API surface stays complete
so app code links, and an unsupported call fails loudly at runtime instead of painting an empty
screen.
