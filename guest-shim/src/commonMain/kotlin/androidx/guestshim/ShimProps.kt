/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.guestshim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * What a `Modifier` chain amounts to once it has been collected: one value per wire prop, each
 * already at its default.
 *
 * A default here is a *reset*, not a "skip". `Updater.set` fires when a value changes back to its
 * default as much as when it leaves it, so [Modifier.toProps] always yields a full set and the
 * sender always writes all of it. Guarding a write with `if (value != default)` is what makes a
 * removed modifier impossible to undo: the host keeps whatever it was last told.
 */
class ShimProps {
    /**
     * Canonical group keys in the order the chain first touched them.
     *
     * Order is meaning, not bookkeeping: the host rebuilds the chain by walking the order props
     * arrived in, and `padding().background()` paints a different picture from
     * `background().padding()` — the first insets then fills, the second fills then insets. A fixed
     * emission order would make the two indistinguishable on the wire.
     */
    val order: MutableList<Int> = mutableListOf()

    internal fun touch(group: Int) {
        if (group !in order) order.add(group)
    }

    var backgroundColor: Int = 0

    // Padding accumulates the way nested upstream padding modifiers do: `.padding(8.dp)
    // .padding(4.dp)` is 12dp of inset, not 4.
    var paddingStart: Float = 0f
    var paddingTop: Float = 0f
    var paddingEnd: Float = 0f
    var paddingBottom: Float = 0f

    // Dp.Unspecified, i.e. "the host decides", matching Dp's own unset encoding.
    var width: Float = Float.NaN
    var height: Float = Float.NaN

    // Fraction of the parent, 0f meaning the modifier was not applied.
    var fillMaxWidth: Float = 0f
    var fillMaxHeight: Float = 0f

    // A shape crosses as a description the host rebuilds, never as an Outline or a Path: 0 is a
    // rectangle, 1 rounded with the four radii below in dp, 2 a circle. The guest cannot resolve a
    // shape itself — createOutline needs the size and density that only the host has at layout.
    var clipShapeType: Int = 0
    var cornerTopStart: Float = 0f
    var cornerTopEnd: Float = 0f
    var cornerBottomEnd: Float = 0f
    var cornerBottomStart: Float = 0f
}

/** A `Modifier.Element` that carries wire props rather than a host-side `Modifier.Node`. */
interface PropElement : Modifier.Element {
    fun applyTo(props: ShimProps)
}

/** Collects the chain outside-in, so a later element wins over an earlier one. */
fun Modifier.toProps(): ShimProps {
    val props = ShimProps()
    foldIn(Unit) { _, element -> if (element is PropElement) element.applyTo(props) }
    return props
}

/**
 * Arrangements and alignments cross as ids: the host holds the real singletons and applies them to
 * a real Compose component, so only the choice has to travel.
 *
 * `Arrangement.spacedBy` and `Alignment` instances built by `Alignment(…)` carry a value rather
 * than being one of the known singletons, and have no id to send; they throw rather than silently
 * arriving as `Top`.
 */
object WireId {
    fun of(arrangement: Arrangement.Vertical): Int =
        when (arrangement) {
            Arrangement.Top -> 0
            Arrangement.Bottom -> 1
            Arrangement.Center -> 2
            Arrangement.SpaceBetween -> 3
            Arrangement.SpaceAround -> 4
            Arrangement.SpaceEvenly -> 5
            else -> throw UnsupportedInGuestException("Arrangement.Vertical $arrangement")
        }

    fun of(arrangement: Arrangement.Horizontal): Int =
        when (arrangement) {
            Arrangement.Start -> 0
            Arrangement.End -> 1
            Arrangement.Center -> 2
            Arrangement.SpaceBetween -> 3
            Arrangement.SpaceAround -> 4
            Arrangement.SpaceEvenly -> 5
            else -> throw UnsupportedInGuestException("Arrangement.Horizontal $arrangement")
        }

    fun of(alignment: Alignment.Horizontal): Int =
        when (alignment) {
            Alignment.Start -> 0
            Alignment.CenterHorizontally -> 1
            Alignment.End -> 2
            else -> throw UnsupportedInGuestException("Alignment.Horizontal $alignment")
        }

    fun of(alignment: Alignment.Vertical): Int =
        when (alignment) {
            Alignment.Top -> 0
            Alignment.CenterVertically -> 1
            Alignment.Bottom -> 2
            else -> throw UnsupportedInGuestException("Alignment.Vertical $alignment")
        }

    fun of(alignment: Alignment): Int =
        when (alignment) {
            Alignment.TopStart -> 0
            Alignment.TopCenter -> 1
            Alignment.TopEnd -> 2
            Alignment.CenterStart -> 3
            Alignment.Center -> 4
            Alignment.CenterEnd -> 5
            Alignment.BottomStart -> 6
            Alignment.BottomCenter -> 7
            Alignment.BottomEnd -> 8
            else -> throw UnsupportedInGuestException("Alignment $alignment")
        }
}
