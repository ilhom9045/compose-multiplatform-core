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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "toggle" screen is `Box(if (flag) Modifier.width(24.dp).background(Color.Red) else Modifier)`.
 *
 * A one-frame harness cannot tell a prop that was never set from one that was set and then taken
 * away — at mount both are a default being written. That is the whole reason `Updater.set` is a
 * trap: guarding a write with `if (value != default)` looks like a saving, is correct at mount, and
 * only shows itself on the frame that turns a modifier back off. So that frame is what these
 * assert.
 */
class ResetTest {

    private fun props(frame: Mutations, keyId: Int): List<Int> =
        frame.propRecords().filter { it[1] == keyId }.map { it[3] }

    @Test
    fun `a modifier taken away resets its prop`() {
        val frames = GuestHarness.runFrames("toggle", true, false)
        assertEquals(3, frames.size, "expected mount plus two frames, got ${frames.size}")

        // Mount: no modifier, so the props go out at their defaults.
        assertEquals(listOf(0), props(frames[0], PropKey.BackgroundColor))
        assertEquals(true, Float.fromBits(props(frames[0], PropKey.Width).single()).isNaN())

        // Applied.
        assertEquals(listOf(0xFFFF0000.toInt()), props(frames[1], PropKey.BackgroundColor))
        assertEquals(24f, Float.fromBits(props(frames[1], PropKey.Width).single()))

        // Taken away. This is the record a guarded write would never send, leaving the host
        // showing a red 24dp box forever.
        assertEquals(listOf(0), props(frames[2], PropKey.BackgroundColor))
        assertEquals(true, Float.fromBits(props(frames[2], PropKey.Width).single()).isNaN())
    }

    @Test
    fun `an unchanged prop is not sent again`() {
        val frames = GuestHarness.runFrames("toggle", true, true)
        assertEquals(2, frames.size, "a frame that changes nothing should not commit")

        // Frame 2 carried the modifier being applied; setting the same flag again recomposes
        // nothing, so there is no third batch at all.
        assertEquals(listOf(24f), props(frames[1], PropKey.Width).map { Float.fromBits(it) })
    }

    @Test
    fun `a changed chain travels whole, not just the prop that changed`() {
        val frames = GuestHarness.runFrames("partial", true)
        assertEquals(2, frames.size)

        // The host clears a node's modifier order on the first modifier prop of a batch and
        // rebuilds it from that batch alone. Sending only the colour would leave the order as
        // [BackgroundColor] and drop the padding out of the chain — the value would still be in the
        // host's map with nothing walking it.
        assertEquals(listOf(0xFF0000FF.toInt()), props(frames[1], PropKey.BackgroundColor))
        assertEquals(listOf(8f), props(frames[1], PropKey.PaddingStart).map { Float.fromBits(it) })
        assertEquals(listOf(0), props(frames[1], PropKey.ClipShapeType))
    }

    @Test
    fun `an idle frame is silent`() {
        // Nothing changes, so the recomposer has nothing to apply and never reaches onEndChanges.
        assertEquals(1, GuestHarness.runFrames("toggle", false).size)
    }
}
