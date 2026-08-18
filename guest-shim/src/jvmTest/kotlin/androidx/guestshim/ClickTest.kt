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
 * The "click" screen is
 * `Column(Modifier.clickable { count++ }) { BasicText("count=$count") }`.
 *
 * This is the first prop that travels both ways. The lambda stays in the guest: the host is told
 * only that a handler exists, and reports back the node id and prop key that fired.
 */
class ClickTest {

    private fun texts(frame: Mutations): List<String> =
        frame.propRecords().filter { it[1] == PropKey.Text }.map { frame.strings[it[3]] }

    @Test
    fun `a handler is announced without the lambda crossing`() {
        val frame = GuestHarness.runFrame("click")
        val record = frame.propRecords().single { it[1] == PropKey.OnClick }
        assertEquals(PropValueType.Callback, record[2])
        // valueBits carries nothing — the host registers a stub keyed by node and prop.
        assertEquals(0, record[3])
    }

    @Test
    fun `a click reported by the host moves guest state and comes back as a new frame`() {
        // Node 1 is the Column, the first node the applier created.
        val frames = GuestHarness.runClicks("click", 1, 1)
        assertEquals(3, frames.size, "mount plus one frame per click")

        assertEquals(listOf("count=0"), texts(frames[0]))
        assertEquals(listOf("count=1"), texts(frames[1]))
        assertEquals(listOf("count=2"), texts(frames[2]))
    }

    @Test
    fun `an event for an unknown node is ignored rather than fatal`() {
        // A node removed in the same frame the host reported a click on is a race the host cannot
        // avoid; it must not take the guest down.
        val frames = GuestHarness.runClicks("click", 999)
        assertEquals(1, frames.size, "nothing recomposed, so no second batch")
    }
}
