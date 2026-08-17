# guest-shim

The Compose API surface as the guest sees it. Compiled to JS, executed inside QuickJS on the
host. Composition runs in the guest; nothing is drawn there. Every visible effect is produced by
the host, which owns the real Compose UI.

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
js actuals wrap skiko — plus everything that transitively needs them: `Brush`, `Shape`,
`Outline`, `drawscope/`, `painter/`, `vector/` (except `PathNode`/`PathBuilder`), `layer/`,
`shadow/`. 41 files, all inside `graphics/`; no value type was touched by the cut.

These are the one place the copy rule does not reach. Their bodies are not transcription — a
guest `Canvas` would have to *record* each call onto the wire, which is the boundary work this
document reserves for `Modifier` and `@Composable`. Copying 3100 lines of declarations that can
only throw would buy nothing until someone needs `Modifier.drawBehind`.

**Known gap:** `Shape` went with them, so `Modifier.background(color, shape)` has no shape
parameter type yet. When it is needed, a shape should cross as a description the host can rebuild
(corner radii, and so on), not as a `Path` — same reasoning as the rest of the wire.

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
