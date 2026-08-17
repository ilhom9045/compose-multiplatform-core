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
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The "layout" screen clips its Column with `RoundedCornerShape(12.dp)` and its Box with
 * `CircleShape`.
 *
 * A shape cannot be resolved on this side: `createOutline` needs the size and density that only the
 * host has at layout time. So it crosses as what it *is* — a type and four radii — and the host
 * rebuilds the real shape.
 */
class ShapeTest {

    private val frame = GuestHarness.runFrame("layout")

    private fun nodeIdOf(nodeTypeId: Int): Int =
        frame.records().single { it[0] == MutationType.Create && it[6] == nodeTypeId }[1]

    private fun prop(nodeTypeId: Int, keyId: Int): Int =
        frame.propRecords().single { it[0] == nodeIdOf(nodeTypeId) && it[1] == keyId }[3]

    @Test
    fun `a rounded shape crosses as four radii in dp`() {
        assertEquals(1, prop(NodeType.Column, PropKey.ClipShapeType))
        assertEquals(12f, Float.fromBits(prop(NodeType.Column, PropKey.CornerRadiusTopStart)))
        assertEquals(12f, Float.fromBits(prop(NodeType.Column, PropKey.CornerRadiusTopEnd)))
        assertEquals(12f, Float.fromBits(prop(NodeType.Column, PropKey.CornerRadiusBottomEnd)))
        assertEquals(12f, Float.fromBits(prop(NodeType.Column, PropKey.CornerRadiusBottomStart)))
    }

    @Test
    fun `a circle crosses as its own type, not as a 50 percent rounding`() {
        // CircleShape is RoundedCornerShape(50), whose corners are percentages and could not be
        // sent as radii at all — it has to be recognised as the circle it is.
        assertEquals(2, prop(NodeType.Box, PropKey.ClipShapeType))
    }

    @Test
    fun `an unclipped node still writes the group, so a removed clip resets`() {
        assertEquals(0, prop(NodeType.Text, PropKey.ClipShapeType))
        assertEquals(0f, Float.fromBits(prop(NodeType.Text, PropKey.CornerRadiusTopStart)))
    }

    @Test
    fun `a percentage corner is refused rather than guessed`() {
        try {
            GuestHarness.runFrame("percentClip")
            fail("expected the guest to refuse a percent-based corner")
        } catch (e: Throwable) {
            val text = generateSequence(e) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
            assertTrue(
                "percent-based CornerSize" in text,
                "expected the guest's own message, got: $text",
            )
        }
    }
}
