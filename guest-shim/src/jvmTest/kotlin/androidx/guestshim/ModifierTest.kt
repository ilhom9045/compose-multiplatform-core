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
 * The "layout" screen composes
 * ```
 * Column(Modifier.padding(8.dp).background(Color.Red).fillMaxWidth(), Arrangement.Center) {
 *     BasicText("hi", Modifier.padding(4.dp).padding(2.dp))
 *     Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {}
 * }
 * ```
 */
class ModifierTest {

    private val frame = GuestHarness.runFrame("layout")

    /** Node id the applier gave the single node of this type. */
    private fun nodeIdOf(nodeTypeId: Int): Int =
        frame.records()
            .filter { it[0] == MutationType.Create && it[6] == nodeTypeId }
            .also { assertEquals(1, it.size, "expected one node of type $nodeTypeId, got $it") }
            .single()[1]

    private fun intProp(nodeTypeId: Int, keyId: Int): Int =
        frame.propRecords()
            .filter { it[0] == nodeIdOf(nodeTypeId) && it[1] == keyId }
            .also { assertEquals(1, it.size, "expected one record for key $keyId, got $it") }
            .single()[3]

    private fun floatProp(nodeTypeId: Int, keyId: Int): Float =
        Float.fromBits(intProp(nodeTypeId, keyId))

    @Test
    fun `each composable becomes one host node`() {
        val created = frame.records().filter { it[0] == MutationType.Create }.map { it[6] }
        assertEquals(listOf(NodeType.Column, NodeType.Text, NodeType.Box), created)
    }

    @Test
    fun `a modifier chain collapses into props on its node`() {
        assertEquals(0xFFFF0000.toInt(), intProp(NodeType.Column, PropKey.BackgroundColor))
        assertEquals(8f, floatProp(NodeType.Column, PropKey.PaddingStart))
        assertEquals(8f, floatProp(NodeType.Column, PropKey.PaddingBottom))
        assertEquals(1f, floatProp(NodeType.Column, PropKey.FillMaxWidth))
        assertEquals(24f, floatProp(NodeType.Box, PropKey.Width))
        assertEquals(24f, floatProp(NodeType.Box, PropKey.Height))
    }

    @Test
    fun `chained padding accumulates the way nested upstream modifiers do`() {
        assertEquals(6f, floatProp(NodeType.Text, PropKey.PaddingTop))
    }

    @Test
    fun `composable parameters cross as ids, not as objects`() {
        // Arrangement.Center and Alignment.Center, per WireId.
        assertEquals(2, intProp(NodeType.Column, PropKey.VerticalArrangement))
        assertEquals(4, intProp(NodeType.Box, PropKey.ContentAlignment))
        // Left at its default, and still sent: a default is a value, not an absence.
        assertEquals(0, intProp(NodeType.Column, PropKey.HorizontalAlignment))
    }

    @Test
    fun `text crosses as a string prop`() {
        val record = frame.propRecords()
            .single { it[0] == nodeIdOf(NodeType.Text) && it[1] == PropKey.Text }
        assertEquals(PropValueType.String, record[2])
        assertEquals("hi", frame.strings[record[3]])
    }

    @Test
    fun `props go out in the order the chain declared them`() {
        // The screen writes Modifier.padding(8.dp).background(Color.Red).fillMaxWidth(). The host
        // rebuilds the chain by walking arrival order, so padding must precede background here —
        // padding-then-background insets before it fills, the other way round fills before it
        // insets, and those are different pictures.
        val column = nodeIdOf(NodeType.Column)
        fun firstIndexOf(keyId: Int) =
            frame.propRecords().indexOfFirst { it[0] == column && it[1] == keyId }

        val padding = firstIndexOf(PropKey.PaddingStart)
        val background = firstIndexOf(PropKey.BackgroundColor)
        val fill = firstIndexOf(PropKey.FillMaxWidth)
        assertEquals(true, padding in 0 until background, "padding=$padding background=$background")
        assertEquals(true, background < fill, "background=$background fill=$fill")

        // Untouched groups follow the declared ones, so they can reset without reordering the rest.
        assertEquals(true, fill < firstIndexOf(PropKey.Width))
    }

    @Test
    fun `an unset prop is still written, so removing a modifier can reset it`() {
        // The Column has no size modifier. Width is written anyway, at Dp.Unspecified — if it were
        // skipped, a later composition that dropped a .width() would leave the host holding the
        // stale value with nothing to correct it.
        assertEquals(true, floatProp(NodeType.Column, PropKey.Width).isNaN())
    }
}
